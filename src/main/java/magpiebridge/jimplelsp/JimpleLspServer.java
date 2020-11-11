package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.frontend.AbstractClassSource;
import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.frontend.SootClassSource;
import de.upb.swt.soot.core.inputlocation.AnalysisInputLocation;
import de.upb.swt.soot.core.inputlocation.EagerInputLocation;
import de.upb.swt.soot.core.jimple.basic.NoPositionInformation;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.views.View;
import de.upb.swt.soot.java.core.JavaIdentifierFactory;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import magpiebridge.core.MagpieServer;
import magpiebridge.core.ServerConfiguration;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.lsp4j.*;

import static magpiebridge.jimplelsp.Util.positionToRange;

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

  public JimpleLspServer() {
    super( new ServerConfiguration());
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
              }catch( ResolveException e){
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
    if (isCacheInvalid) {
      view = recreateView();
      isCacheInvalid = false;
    }
    return view;
  }

  @Nonnull
  private View recreateView() {
    Map<ClassType, AbstractClassSource> map = new HashMap<>();
    textDocumentClassMapping.forEach((key, value) -> map.put(value.getClassType(), value));
    final JimpleProject project = new JimpleProject(new EagerInputLocation( map));
    return project.createFullView();
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
      SootClassSource scs = jimpleConverter.run(charStream, new EagerInputLocation(), Util.uriToPath(uri));
      // input is clean
      final SootClassSource overriden = textDocumentClassMapping.put(uri, scs);
      if (overriden != null) {
        // possible optimization: compare if classes are still equal -> set dirty bit only when necessary
      }
      isCacheInvalid = true;
      return true;
    } catch (ResolveException e) {
      // feed error into diagnostics
      final Diagnostic d = new Diagnostic(positionToRange(e.getRange()), e.getMessage(), DiagnosticSeverity.Error, "JimpleParser");
      client.publishDiagnostics(new PublishDiagnosticsParams( uri, Collections.singletonList(d) ));
      return false;
    } catch (Exception e) {
      // feed error into diagnostics
      final Diagnostic d = new Diagnostic( new Range(new Position(0,0), new Position(1,0)), e.getMessage(), DiagnosticSeverity.Error, "JimpleParser");
      // FIXME: merge with other diagnostics in magpie
      client.publishDiagnostics(new PublishDiagnosticsParams( uri, Collections.singletonList(d) ));
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
    workspaceFolders = params.getWorkspaceFolders();

    final CompletableFuture<InitializeResult> initialize = super.initialize(params);
    try {
      final ServerCapabilities capabilities = initialize.get().getCapabilities();
      capabilities.setWorkspaceSymbolProvider(true);
      capabilities.setDocumentSymbolProvider(true);

      capabilities.setImplementationProvider(true);
      capabilities.setDefinitionProvider(true);

      // TODO: capabilities.setTypeDefinitionProvider(true);
      // TODO: capabilities.setReferencesProvider(true);

      // check: capabilities.setDocumentFormattingProvider(true);
      // check: capabilities.setFoldingRangeProvider(true);

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


  // TODO: remove
  private List<Diagnostic> validate(DidOpenTextDocumentParams model) {

    List<Diagnostic> res = new ArrayList<>();

    Diagnostic diagnostic = new Diagnostic();
    diagnostic.setSeverity(DiagnosticSeverity.Error);
    diagnostic.setMessage("The .jimple file contains an Error");
    diagnostic.setSource("JimpleParser");
    int line = 5;
    int charOffset = 4;
    int length = 10;
    diagnostic.setRange(
            new Range(new Position(line, charOffset), new Position(line, charOffset + length)));

    final ArrayList<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();
    relatedInformation.add(
            new DiagnosticRelatedInformation(
                    new Location(model.getTextDocument().getUri(), new Range(new Position(1, 5), new Position(2, 0))),
                    "here is sth strange"));

    boolean uriOne = model.getTextDocument().getUri().endsWith("helloworld.jimple");
    String uriA =
            "file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/"
                    + (uriOne ? "another" : "helloworld")
                    + ".jimple";
    String uriB =
            "file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/"
                    + (!uriOne ? "another" : "helloworld")
                    + ".jimple";

    relatedInformation.add(
            new DiagnosticRelatedInformation(
                    new Location(uriA, new Range(new Position(2, 0), new Position(3, 0))),
                    "more strangeness"));
    diagnostic.setRelatedInformation(relatedInformation);

    res.add(diagnostic);

    Diagnostic diagnostic2 = new Diagnostic();
    diagnostic2.setMessage("This is a Jimple Error. ");
    diagnostic2.setRange(new Range(new Position(2, 0), new Position(2, 10)));
    diagnostic2.setSource("Jimpleparser");
    diagnostic2.setCode("403 Forbidden");
    diagnostic2.setSeverity(DiagnosticSeverity.Error);

    List<DiagnosticRelatedInformation> related = new ArrayList<>();
    related.add(
            new DiagnosticRelatedInformation(
                    new Location(uriA, new Range(new Position(4, 4), new Position(4, 10))), "problem A"));
    related.add(
            new DiagnosticRelatedInformation(
                    new Location(uriB, new Range(new Position(6, 6), new Position(6, 12))),
                    "another problem: B"));
    related.add(
            new DiagnosticRelatedInformation(
                    new Location(uriA, new Range(new Position(8, 8), new Position(8, 14))), "problem C"));
    diagnostic2.setRelatedInformation(related);

    res.add(diagnostic2);
    return res;
  }


  @Nullable
  public ClassType uriToClasstype(@Nonnull String strUri) {
    final SootClassSource scs = textDocumentClassMapping.get(strUri);
    if(scs == null){
      return null;
    }
    return scs.getClassType();
  }

}
