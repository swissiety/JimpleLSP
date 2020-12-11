package magpiebridge.jimplelsp;

import static magpiebridge.jimplelsp.Util.positionToRange;

import de.upb.swt.soot.core.frontend.AbstractClassSource;
import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.frontend.SootClassSource;
import de.upb.swt.soot.core.inputlocation.EagerInputLocation;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.views.View;
import de.upb.swt.soot.jimple.parser.JimpleConverter;
import de.upb.swt.soot.jimple.parser.JimpleProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.ServerConfiguration;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.eclipse.lsp4j.*;

/** @author Markus Schmidt */
public class JimpleLspServer extends MagpieServer {

  @Nonnull private final Map<String, SootClassSource> textDocumentClassMapping = new HashMap<>();
  private View view;
  private boolean isViewDirty = true;

  public JimpleLspServer() {
    super(new ServerConfiguration());
  }

  public JimpleLspServer(ServerConfiguration config) {
    super(config);
    this.textDocumentService = new JimpleTextDocumentService(this);
    this.workspaceService = new JimpleWorkspaceService(this);
  }

  @Nonnull
  public <T> CompletableFuture<T> pool(Callable<T> lambda) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return lambda.call();
          } catch (ResolveException e) {
            // TODO: send publishDiagnostics!
            e.printStackTrace();
          } catch (Throwable e) {
            e.printStackTrace();
          }
          return null;
        },
        THREAD_POOL);
  }

  @Nonnull
  ClientCapabilities getClientCapabilities() {
    return clientConfig;
  }

  @Nonnull
  public View getView() {
    if (isViewDirty) {
      view = recreateView();
      isViewDirty = false;
    }
    return view;
  }

  @Nonnull
  private View recreateView() {
    Map<ClassType, AbstractClassSource> map = new HashMap<>();
    textDocumentClassMapping.forEach((key, value) -> map.put(value.getClassType(), value));
    final JimpleProject project = new JimpleProject(new EagerInputLocation(map));
    return project.createFullView();
  }

  public boolean quarantineInputOrUpdate(@Nonnull String uri) throws ResolveException, IOException {
    return quarantineInputOrUpdate(uri, CharStreams.fromPath(Util.uriToPath(uri)));
  }

  public boolean quarantineInputOrUpdate(@Nonnull String uri, String content)
      throws ResolveException {
    return quarantineInputOrUpdate(uri, CharStreams.fromString(content));
  }

  private boolean quarantineInputOrUpdate(@Nonnull String uri, @Nonnull CharStream charStream)
      throws ResolveException {
    final JimpleConverter jimpleConverter = new JimpleConverter();
    try {
      SootClassSource scs =
          jimpleConverter.run(charStream, new EagerInputLocation(), Util.uriToPath(uri));
      // input is clean
      final SootClassSource overriden = textDocumentClassMapping.put(uri, scs);
      if (overriden != null) {
        // possible optimization: compare if classes are still equal -> set dirty bit only when
        // necessary
      }
      isViewDirty = true;
      return true;
    } catch (ResolveException e) {
      // feed error into diagnostics
      final Diagnostic d =
          new Diagnostic(
              positionToRange(e.getRange()),
              e.getMessage(),
              DiagnosticSeverity.Error,
              "JimpleParser");
      client.publishDiagnostics(new PublishDiagnosticsParams(uri, Collections.singletonList(d)));
      return false;
    } catch (Exception e) {
      // feed error into diagnostics
      final Diagnostic d =
          new Diagnostic(
              new Range(new Position(0, 0), new Position(1, 0)),
              e.getMessage(),
              DiagnosticSeverity.Error,
              "JimpleParser");
      // FIXME: merge with other diagnostics in magpie
      client.publishDiagnostics(new PublishDiagnosticsParams(uri, Collections.singletonList(d)));
      return false;
    }
  }

  @Override
  public void exit() {
    // FIXME: don't die in development
    logger.cleanUp();
    MagpieServer.ExceptionLogger.cleanUp();
  }

  List<WorkspaceFolder> workspaceFolders = Collections.emptyList();

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    if(params.getWorkspaceFolders() != null ) {
      workspaceFolders = params.getWorkspaceFolders();
    }

    final CompletableFuture<InitializeResult> initialize = super.initialize(params);
    try {
      final ServerCapabilities capabilities = initialize.get().getCapabilities();
      capabilities.setWorkspaceSymbolProvider(true);
      capabilities.setDocumentSymbolProvider(true);

      capabilities.setImplementationProvider(true);
      capabilities.setDefinitionProvider(true);
      capabilities.setReferencesProvider(true);
      capabilities.setHoverProvider(true);

      capabilities.setTypeDefinitionProvider(true);
      capabilities.setFoldingRangeProvider(true);
      capabilities.setDocumentHighlightProvider(true);
      // check: capabilities.setDocumentFormattingProvider(true);

    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
      return null;
    }

    return initialize;
  }

  @Override
  public void initialized(InitializedParams params) {
    super.initialized(params);

    long startNanos = System.nanoTime();
    // scan workspace all jimple files <-> classes
    // hint: its expensive - maybe do it asynchrounous?
    List<Path> rootpaths = new ArrayList<>(workspaceFolders.size() + 1);
    rootPath.ifPresent(rootpaths::add);

    workspaceFolders.forEach(
        f -> {
          final Path path = Util.uriToPath(f.getUri()).toAbsolutePath();
          if (rootpaths.stream()
              .noneMatch(existingPath -> path.startsWith(existingPath.toAbsolutePath()))) {
            // add workspace folder if its not a subdirectory of an already existing path
            rootpaths.add(path);
          }
        });
    List<Path> apkFiles = new ArrayList<>();
    List<Path> jimpleFiles = new ArrayList<>();

    // scan all workspaces in depth for jimple files
    for (Path rootpath : rootpaths) {
      // jimple
      try (Stream<Path> paths = Files.walk(rootpath)) {
        paths.filter(f -> f.toString().endsWith(".jimple")).forEach(jimpleFiles::add);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    // TODO: nice2have: implement asking to extract jimple from an apk with old soot
    /* find apk in top levels/first level subdir
    if(jimpleFiles.isEmpty()){
      for (Path rootpath : rootpaths) {
        // find apk
        try (Stream<Path> paths = Files.walk(rootpath)) {
          paths.filter(f -> f.toString().endsWith(".apk")).forEach(apkFiles::add);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    */

    for (Path jimpleFile : jimpleFiles) {
      try {
        final String uri = Util.pathToUri(jimpleFile);
        quarantineInputOrUpdate(uri);
      } catch (IOException exception) {
        exception.printStackTrace();
      }
    }

    double runtimeMs = (System.nanoTime() - startNanos) / 1e6;
    // TODO: channel to info log if necessary: System.out.println("Workspace indexing took " + runtimeMs + " ms");
  }

  @Nullable
  public ClassType docIdentifierToClassType(@Nonnull String textDocument) {
    final SootClassSource sootClassSource = textDocumentClassMapping.get(textDocument);
    if (sootClassSource == null) {
      return null;
    }
    return sootClassSource.getClassType();
  }

  @Nullable
  public ClassType uriToClasstype(@Nonnull String strUri) {
    final SootClassSource scs = textDocumentClassMapping.get(strUri);
    if (scs == null) {
      return null;
    }
    return scs.getClassType();
  }
}
