package com.github.swissiety.jimplelsp;

import com.github.swissiety.jimplelsp.actions.ProjectSetupAction;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

public class JimpleLanguageServer implements LanguageServer, LanguageClientAware {

  private static final ExecutorService executor = Executors.newSingleThreadExecutor();

  private void pool(Runnable runnable) {
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


    final Iterable<? extends WorkspaceFolder> workspaceFolders = Collections.singleton(new WorkspaceFolder(""));
    final boolean workspaceEditSupport = params.getCapabilities().getWorkspace().getApplyEdit();

    pool(() -> {

      try {
        initialized.get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }

      assert getClient() != null;
      ProjectSetupAction.scanWorkspace(client, workspaceFolders, workspaceEditSupport);

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

    });

    return CompletableFuture.completedFuture(new InitializeResult(capabilities));
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
}
