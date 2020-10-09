package com.github.swissiety.jimplelsp;

import com.github.swissiety.jimplelsp.actions.ProjectSetupAction;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.upb.swt.soot.core.inputlocation.AnalysisInputLocation;
import de.upb.swt.soot.core.model.Position;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.jimple.parser.JimpleAnalysisInputLocation;
import de.upb.swt.soot.jimple.parser.JimpleProject;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

public class JimpleLanguageServer implements LanguageServer, LanguageClientAware {

  private static final ExecutorService executor = Executors.newSingleThreadExecutor();

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

  @Nonnull
  List<AnalysisInputLocation> analysisInputLocations = new ArrayList<>();
  ;
  @Nonnull
  private Collection<SootClass> classes = Collections.emptyList();


  public LanguageClient getClient() {
    return client;
  }

  public JimpleLanguageServer() {
    textService = new JimpleTextDocumentService(this);
    workspaceService = new JimpleWorkspaceService(this);
  }

  private void addInputLocation(String inputPath) {
    final JimpleAnalysisInputLocation analysisInputLocation = new JimpleAnalysisInputLocation(Paths.get(inputPath));
    if (!analysisInputLocations.contains(analysisInputLocation)) {
      analysisInputLocations.add(analysisInputLocation);
      refreshClasses(false);
    }
  }

  private void removeInputLocation(String inputPath) {
    analysisInputLocations.remove(new JimpleAnalysisInputLocation(Paths.get(inputPath)));
    refreshClasses(false);
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

    // TODO: check if lsp4j allows sending requests that the client cant handle due to
    // ClientCapabilities

    final ServerCapabilities sCapabilities = new ServerCapabilities();
    //		sCapabilities.setCodeActionProvider(new CodeActionOptions());
    //		sCapabilities.setDefinitionProvider(Boolean.TRUE);
    //		sCapabilities.setHoverProvider(Boolean.TRUE);
    //		sCapabilities.setReferencesProvider(Boolean.TRUE);

    final TextDocumentSyncOptions textDocumentSync = new TextDocumentSyncOptions();
    textDocumentSync.setOpenClose(true);
    sCapabilities.setTextDocumentSync(textDocumentSync);

    //    sCapabilities.setDocumentSymbolProvider(Boolean.TRUE);
    //		sCapabilities.setCodeLensProvider(new CodeLensOptions(true));


    final List<? extends WorkspaceFolder> workspaceFolders = params.getWorkspaceFolders() == null ? Collections.emptyList() : params.getWorkspaceFolders();
    final ClientCapabilities cCapabilities = params.getCapabilities();
    final WorkspaceClientCapabilities workspace = cCapabilities.getWorkspace();
    final boolean workspaceEditSupport = workspace.getApplyEdit();

    workspaceFolders.forEach(folder -> analysisInputLocations.add(uriToPath(folder.getUri())));

    pool(() -> {

      try {
        initialized.get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
        return;
      }

      // file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/helloworld.jimple
      ProjectSetupAction.scanWorkspace(client, workspaceFolders, workspaceEditSupport);

    });

    return CompletableFuture.completedFuture(new InitializeResult(sCapabilities));
  }


  private AnalysisInputLocation uriToPath(String uri) {
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


  public Collection<SootClass> getClasses() {
    if (classes.isEmpty()) {
      refreshClasses(true);
    }
    return classes;
  }

  private void refreshClasses(boolean replace) {
    // hint: update list in Modifiable usecase
    JimpleProject project = new JimpleProject(analysisInputLocations);
    final Collection<SootClass> classes = project.createOnDemandView().getClasses();
    if (replace) {
      this.classes = classes;
    } else {
      this.classes.addAll(classes);
    }
  }

  public Range positionToRange(Position position) {
    return new Range(new org.eclipse.lsp4j.Position(position.getFirstLine(), position.getFirstCol()), new org.eclipse.lsp4j.Position(position.getLastLine(), position.getLastCol()));
  }
}
