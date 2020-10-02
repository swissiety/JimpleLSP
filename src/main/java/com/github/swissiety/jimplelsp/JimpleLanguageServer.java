package com.github.swissiety.jimplelsp;

import com.github.swissiety.jimplelsp.actions.ProjectSetupAction;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

public class JimpleLanguageServer implements LanguageServer, LanguageClientAware {

  @Nonnull
  private final TextDocumentService textService;
  @Nonnull
  private final WorkspaceService workspaceService;
  @Nullable
  private LanguageClient client = null;
  private InitializeParams params;

  public LanguageClient getClient() {
    return client;
  }

  public JimpleLanguageServer() {
    textService = new JimpleTextDocumentService(this, client);
    workspaceService = new JimpleWorkspaceService();
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

    // TODO: save clientCapabilities etc.
    /*
    System.err.println("rootUri:" + params.getRootUri());
    for (WorkspaceFolder workspaceFolder : params.getWorkspaceFolders()) {
      System.err.println(
          "workspacefolder: " + workspaceFolder.getName() + ": " + workspaceFolder.getUri());
    }
    */

    // TODO: check if lsp4j allows sending requests that the client cant handle due to
    // ClientCapabilities

    final ServerCapabilities capabilities = new ServerCapabilities();
    //		capabilities.setCodeActionProvider(new CodeActionOptions());
    //		capabilities.setDefinitionProvider(Boolean.TRUE);
    //		capabilities.setHoverProvider(Boolean.TRUE);
    //		capabilities.setReferencesProvider(Boolean.TRUE);
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
    //    capabilities.setDocumentSymbolProvider(Boolean.TRUE);
    //		capabilities.setCodeLensProvider(new CodeLensOptions(true));

    return CompletableFuture.completedFuture(new InitializeResult(capabilities));
  }

  @Override
  public void initialized(InitializedParams params) {
    init();
  }

  void init() {


    getClient().showMessage(new MessageParams(MessageType.Info, "Hello, it's me!"));


    //    getClient().showMessage(new MessageParams(MessageType.Info, "indexing jimple data.."));

    /* TODO: check if its supported before requesting
    final CompletableFuture<List<WorkspaceFolder>> listCompletableFuture =
            JimpleLanguageServer.getClient().workspaceFolders();
    final List<WorkspaceFolder> workspaceFolders;
    try {
      workspaceFolders = listCompletableFuture.get();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
      return;
    }
*/
    final Iterable<? extends WorkspaceFolder> workspaceFolders = Collections.singleton(new WorkspaceFolder(""));
    final boolean workspaceEditSupport = params.getCapabilities().getWorkspace().getApplyEdit().booleanValue();

    ProjectSetupAction.scanWorkspace(client, workspaceFolders, workspaceEditSupport);

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
    init();
  }
}
