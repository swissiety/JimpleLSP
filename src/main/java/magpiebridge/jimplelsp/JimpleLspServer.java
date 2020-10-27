package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.frontend.SootClassSource;
import de.upb.swt.soot.core.inputlocation.AnalysisInputLocation;
import de.upb.swt.soot.core.inputlocation.EagerInputLocation;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.views.View;
import de.upb.swt.soot.jimple.parser.JimpleConverter;
import de.upb.swt.soot.jimple.parser.JimpleProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

// FIXME: sth with sourcefilemanager
public class JimpleLspServer extends MagpieServer {

  @Nonnull
  private boolean isCacheInvalid = true;

  @Nonnull
  private View view;
  @Nonnull
  private List<AnalysisInputLocation> analysisInputLocations = new ArrayList<>();
  @Nonnull
  private Map<String, SootClassSource> textDocumentClassMapping = new HashMap<>();

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
              } catch (Throwable e) {
                e.printStackTrace();
                return null;
              }
            },
            THREAD_POOL);
  }

  @Nonnull
  ClientCapabilities getClientCapabilities() {
    return clientConfig;
  }

  @Nonnull
  public View getView() {
    if (isCacheInvalid) {
      view = recreateView();
      isCacheInvalid = false;
    }
    return view;
  }

  @Nonnull
  private View recreateView() {
    //FIXME: use cached classources to rebuild
    analysisInputLocations.add(new EagerInputLocation());
    final JimpleProject project = new JimpleProject(analysisInputLocations);
    return project.createOnDemandView();
  }

  public boolean quarantineInputOrUpdate(@Nonnull String uri) throws ResolveException, IOException {
    return quarantineInputOrUpdate(uri, CharStreams.fromPath(Util.uriToPath(uri)));
  }

  public boolean quarantineInputOrUpdate(@Nonnull String uri, String content) throws ResolveException {
    return quarantineInputOrUpdate(uri, CharStreams.fromString(content));
  }

  private boolean quarantineInputOrUpdate(@Nonnull String uri, @Nonnull CharStream charStream) throws ResolveException {
    final JimpleConverter jimpleConverter = new JimpleConverter();
    try {
      SootClassSource scs = jimpleConverter.run(charStream, new EagerInputLocation(), Paths.get(uri));
      // input is clean
      final SootClassSource overriden = this.textDocumentClassMapping.put(uri, scs);
      if (overriden != null) {
        // possible optimization: compare if classes are still equal -> set dirty bit only when necessary
      }
      isCacheInvalid = true;
      return true;
    } catch (ResolveException e) {
      // feed error into diagnostics
      // FIXME uncomment addDiagnostic(uri, new Diagnostic(positionToRange(e.getRange()),
      // e.getMessage(), DiagnosticSeverity.Error, "JimpleParser"));
      return false;
    }
  }

  List<WorkspaceFolder> workspaceFolders = Collections.emptyList();

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    workspaceFolders = params.getWorkspaceFolders();

    final CompletableFuture<InitializeResult> initialize = super.initialize(params);
    try {
      final ServerCapabilities capabilities = initialize.get().getCapabilities();
      capabilities.setWorkspaceSymbolProvider(true);
      capabilities.setDocumentSymbolProvider(true);
      // TODO: capabilities.setDefinitionProvider(true);
      // TODO: capabilities.setTypeDefinitionProvider(true);
      // TODO: capabilities.setImplementationProvider(true);
      // TODO: capabilities.setReferencesProvider(true);

      capabilities.setDocumentFormattingProvider(true);
      capabilities.setFoldingRangeProvider(true);

    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
      return null;
    }

    return initialize;
  }

  @Override
  public void initialized(InitializedParams params) {
    super.initialized(params);
    // scan workspace all jimple files <-> classes
    // hint: its expensive - maybe do it asynchrounous?
    List<Path> rootpaths = new ArrayList<>(workspaceFolders.size() + 1);
    rootPath.ifPresent(rootpaths::add);

    workspaceFolders.forEach(f -> {
      final Path path = Util.uriToPath(f.getUri()).toAbsolutePath();
      if (!rootpaths.stream().anyMatch(existingPath -> path.startsWith(existingPath.toAbsolutePath()))) {
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

  }

  @Nullable
  public ClassType docIdentifierToClassType(@Nonnull String textDocument) {
    final SootClassSource sootClassSource = textDocumentClassMapping.get(textDocument);
    if (sootClassSource == null) {
      return null;
    }
    return sootClassSource.getClassType();
  }
}
