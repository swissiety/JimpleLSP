package com.github.swissiety.jimplelsp;

import magpiebridge.core.MagpieClient;
import org.eclipse.lsp4j.*;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SemanticTokenTest {

  class LspClient implements MagpieClient {
    private final List<WorkspaceFolder> worskspaceFolders;

    public LspClient(List<Path> worskspaceFolders) {
      this.worskspaceFolders =
          worskspaceFolders.stream()
              .map(f -> new WorkspaceFolder(Util.pathToUri(f)))
              .collect(Collectors.toList());
    }

    public void connectTo(JimpleLspServer server) {
      server.connect(this);
      server.initialize(getInitializeParams());
      server.initialized(new InitializedParams());
    }

    private InitializeParams getInitializeParams() {
      final InitializeParams params = new InitializeParams();
      // params.setRootUri(Util.pathToUri(root));
      ClientCapabilities clientCaps = new ClientCapabilities();
      TextDocumentClientCapabilities docCaps = new TextDocumentClientCapabilities();
      docCaps.setDeclaration(new DeclarationCapabilities(true));
      docCaps.setDefinition(new DefinitionCapabilities(true));
      docCaps.setReferences(new ReferencesCapabilities(true));
      docCaps.setImplementation(new ImplementationCapabilities(true));
      docCaps.setDocumentSymbol(new DocumentSymbolCapabilities(true));
      docCaps.setHover(new HoverCapabilities(true));

      clientCaps.setTextDocument(docCaps);

      WorkspaceClientCapabilities wCaps = new WorkspaceClientCapabilities();
      wCaps.setSymbol(
          new SymbolCapabilities(
              new SymbolKindCapabilities(
                  Arrays.asList(SymbolKind.Class, SymbolKind.Method, SymbolKind.Field))));
      clientCaps.setWorkspace(wCaps);

      params.setCapabilities(clientCaps);
      return params;
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(
        ApplyWorkspaceEditParams params) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void telemetryEvent(Object o) {}

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams publishDiagnosticsParams) {}

    @Override
    public void showMessage(MessageParams messageParams) {}

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(
        ShowMessageRequestParams showMessageRequestParams) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams messageParams) {
      final String str = messageParams.getType() + ": " + messageParams.getMessage();
      System.out.println(str);
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
      return CompletableFuture.completedFuture(worskspaceFolders);
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void notifyProgress(ProgressParams params) {}

    @Override
    public void logTrace(LogTraceParams params) {}

    @Override
    public void setTrace(SetTraceParams params) {}

    @Override
    public CompletableFuture<Void> refreshSemanticTokens() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> refreshCodeLenses() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void showHTML(String content) {}

    @Override
    public CompletableFuture<Map<String, String>> showInputBox(List<String> messages) {
      return null;
    }
  }

  @Test
  public void partialValidInputTest_justStmt() {
    JimpleLspServer server = new JimpleLspServer();
    final Path workspaceFolder = Paths.get("src/test/resources/partial_invalid_inputs/");
    assertTrue(Files.exists(workspaceFolder));

    LspClient client = new LspClient(Collections.singletonList(workspaceFolder));
    client.connectTo(server);

    Path path = Paths.get("src/test/resources/partial_invalid_inputs/invalid_juststmt.jimple");
    CompletableFuture<SemanticTokens> semanticTokensCompletableFuture =
            server
                    .getTextDocumentService()
                    .semanticTokensFull(
                            new SemanticTokensParams(new TextDocumentIdentifier(Util.pathToUri(path))));
    try {
      final SemanticTokens semanticTokens = semanticTokensCompletableFuture.get();
      assertNotNull(semanticTokens);
      System.out.println(semanticTokens.getData());

    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void partialValidInputTest_justStmts() {
    JimpleLspServer server = new JimpleLspServer();
    final Path workspaceFolder = Paths.get("src/test/resources/partial_invalid_inputs/");
    assertTrue(Files.exists(workspaceFolder));

    LspClient client = new LspClient(Collections.singletonList(workspaceFolder));
    client.connectTo(server);

    Path path = Paths.get("src/test/resources/partial_invalid_inputs/invalid_juststmts.jimple");
    CompletableFuture<SemanticTokens> semanticTokensCompletableFuture =
            server
                    .getTextDocumentService()
                    .semanticTokensFull(
                            new SemanticTokensParams(new TextDocumentIdentifier(Util.pathToUri(path))));
    try {
      final SemanticTokens semanticTokens = semanticTokensCompletableFuture.get();
      assertNotNull(semanticTokens);
      System.out.println(semanticTokens.getData());

    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }
}
