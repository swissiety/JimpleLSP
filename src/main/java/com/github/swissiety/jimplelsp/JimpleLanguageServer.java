package com.github.swissiety.jimplelsp;

import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.frontend.SootClassSource;
import de.upb.swt.soot.core.inputlocation.AnalysisInputLocation;
import de.upb.swt.soot.core.inputlocation.EagerInputLocation;
import de.upb.swt.soot.core.model.Position;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.model.SourceType;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.views.View;
import de.upb.swt.soot.java.core.JavaIdentifierFactory;
import de.upb.swt.soot.jimple.parser.JimpleAnalysisInputLocation;
import de.upb.swt.soot.jimple.parser.JimpleConverter;
import de.upb.swt.soot.jimple.parser.JimpleProject;
import org.antlr.v4.runtime.CharStreams;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JimpleLanguageServer implements LanguageServer, LanguageClientAware {

  final AnalysisInputLocation dummyInputLocation = new EagerInputLocation();
  private static final ExecutorService executor = Executors.newSingleThreadExecutor();
  private boolean validCache = false;

  public void pool(Runnable runnable) {
    CompletableFuture.runAsync(() -> {
      try {
        runnable.run();
      } catch (Throwable e) {
        e.printStackTrace();
      }
    }, executor);
  }

  @Nonnull
  private final TextDocumentService textService;
  @Nonnull
  private final WorkspaceService workspaceService;
  @Nullable
  private LanguageClient client = null;
  private InitializeParams params;
  private CompletableFuture<Boolean> initialized = new CompletableFuture<>();

  List<Diagnostic> diagnostics = new ArrayList<>();


  @Nonnull
  List<AnalysisInputLocation> analysisInputLocations = new ArrayList<>();
  @Nonnull
  private Collection<SootClass> classes = Collections.emptyList();

  @Nonnull
  private Map<String, SootClass> fileToClassMapping = new HashMap<>();

  @Nullable
  SootClass quarantineInput(String uri, String content) throws ResolveException {
    final JimpleConverter jimpleConverter = new JimpleConverter();
    try {
      SootClassSource scs = jimpleConverter.run(CharStreams.fromString(content, uri), dummyInputLocation, Paths.get(uri));
      return scs.buildClass(SourceType.Application);
    } catch (ResolveException e) {
      // feed error into diagnostics
      addDiagnostic(uri, new Diagnostic(positionToRange(e.getRange()), e.getMessage(), DiagnosticSeverity.Error, "JimpleParser"));
      return null;
    }
  }

  private void addDiagnostic(String fileUri, Diagnostic diag) {
    diagnostics.add(diag);
    // TODO: refresh
  }


  View updateAndReplaceView(View v, SootClassSource sc) {
    // FIXME
    analysisInputLocations.add(new EagerInputLocation());
    final JimpleProject project = new JimpleProject(analysisInputLocations);
    return project.createOnDemandView();
  }


  public LanguageClient getClient() {
    return client;
  }

  public JimpleLanguageServer() {
    textService = new JimpleTextDocumentService(this);
    workspaceService = new JimpleWorkspaceService(this);
  }


  /**
   * wants an uri returns an (absolute) path as String
   */
  private String determineInputLocationRoot(String inputUri) {
    // FIXME: implement more fault tolerant solution.. think about subfolder/package matching
    return "/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/";
    /*
    JimpleProject p = new JimpleProject( uriToAnalysisInputLocation(inputUri));
    // improve speed - only resolve one file - previously found by filesearch ;)
    final Optional<SootClass> first = p.createOnDemandView().getClasses().stream().findFirst();
    if( first.isPresent()){
      first.get().getType().getPackageName().toString();
      return inputUri;
    }else{
      // try subfolder
    }
    */

  }

  private void addInputLocation(String inputPath) {
    inputPath = determineInputLocationRoot(inputPath);
    final JimpleAnalysisInputLocation analysisInputLocation = new JimpleAnalysisInputLocation(Paths.get(inputPath));
    if (!analysisInputLocations.contains(analysisInputLocation)) {
      analysisInputLocations.add(analysisInputLocation);
      validCache = false;
    }
  }

  private void removeInputLocation(String inputPath) {
    analysisInputLocations.remove(new JimpleAnalysisInputLocation(Paths.get(inputPath)));
    validCache = false;
  }


  public ClientCapabilities getClientCapabilities() {
    // TODO: adapt so that dynamically registered caps are listed, too
    return params.getCapabilities();
  }

  public InitializeParams getClientParams() {
    return params;
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    this.params = params;

    final ServerCapabilities sCapabilities = new ServerCapabilities();
    //		sCapabilities.setCodeActionProvider(new CodeActionOptions());
    //		sCapabilities.setDefinitionProvider(Boolean.TRUE);
    //		sCapabilities.setHoverProvider(Boolean.TRUE);
    //		sCapabilities.setReferencesProvider(Boolean.TRUE);

    final TextDocumentSyncOptions textDocumentSync = new TextDocumentSyncOptions();
    textDocumentSync.setOpenClose(true);
    textDocumentSync.setChange(TextDocumentSyncKind.Full);
    sCapabilities.setTextDocumentSync(textDocumentSync);

    //sCapabilities.setWorkspaceSymbolProvider( false);

    //    sCapabilities.setDocumentSymbolProvider(Boolean.TRUE);
    //		sCapabilities.setCodeLensProvider(new CodeLensOptions(true));


    final ClientCapabilities cCapabilities = params.getCapabilities();
    final WorkspaceClientCapabilities workspace = cCapabilities.getWorkspace();
    final boolean workspaceEditSupport = workspace.getApplyEdit();

    pool(() -> {

      try {
        initialized.get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
        return;
      }

      if (params.getRootUri() != null) {
        addInputLocation(params.getRootUri());
      }
      final List<? extends WorkspaceFolder> workspaceFolders = params.getWorkspaceFolders() == null ? Collections.emptyList() : params.getWorkspaceFolders();
      workspaceFolders.forEach(folder -> addInputLocation(folder.getUri()));

      // file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/helloworld.jimple
      // TODO: ProjectSetupAction.scanWorkspace(client, workspaceFolders, workspaceEditSupport);

    });

    return CompletableFuture.completedFuture(new InitializeResult(sCapabilities));
  }


  private AnalysisInputLocation uriToAnalysisInputLocation(String uri) {
    // TODO: check if path exists (on this machine) ?
    Path path = Paths.get(uri);
    return new JimpleAnalysisInputLocation(path);
  }

  @Override
  public void initialized(InitializedParams params) {
    initialized.complete(true);
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    return CompletableFuture.supplyAsync(() -> Boolean.TRUE);
  }

  @Override
  public void exit() {
    // System.exit(0);
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return this.textService;
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return this.workspaceService;
  }

  @Override
  public void connect(LanguageClient client) {
    this.client = client;
  }

  String classToUri(SootClass clazz) {
    return "file://" + clazz.getClassSource().getSourcePath();
  }

  ClassType uritoClasstype(String strUri) {
    final String baseName = FilenameUtils.getBaseName(strUri);
    return JavaIdentifierFactory.getInstance().getClassType(baseName);
  }


  public Collection<SootClass> getClasses() {
    if (!validCache) {
      refreshClasses(true);
    }
    return classes;
  }

  private void refreshClasses(boolean replace) {
    // hint: update list in Modifiable usecase
    JimpleProject project = new JimpleProject(analysisInputLocations);
    try {
      final Collection<SootClass> classes = project.createOnDemandView().getClasses();
      if (replace) {
        this.classes = classes;
      } else {
        this.classes.addAll(classes);
      }
    } catch (ResolveException e) {
      // TODO: clear old diagnostics for this file
      diagnostics.add(new Diagnostic(positionToRange(e.getRange()), e.getMessage(), DiagnosticSeverity.Error, e.getInputUri()));
    }
  }

  public Range positionToRange(Position position) {
    return new Range(new org.eclipse.lsp4j.Position(position.getFirstLine(), position.getFirstCol()), new org.eclipse.lsp4j.Position(position.getLastLine(), position.getLastCol()));
  }
}
