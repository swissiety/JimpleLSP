package com.github.swissiety.jimplelsp;

import com.github.swissiety.jimplelsp.provider.JimpleSymbolProvider;
import com.github.swissiety.jimplelsp.resolver.SignaturePositionResolver;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;
import sootup.core.model.SootClass;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** @author Markus Schmidt */
public class JimpleWorkspaceService implements WorkspaceService {
    private final JimpleLspServer server;

    public JimpleWorkspaceService(JimpleLspServer server) {
        this.server = server;
    }

  JimpleLspServer getServer() {
    return (JimpleLspServer) server;
  }

  @Override
  public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
    return getServer()
        .pool(
            () -> {
              int limit = 32;
              List<? extends SymbolInformation> list = new ArrayList<>(limit);

              final String query = params.getQuery().trim().toLowerCase();

              // start searching if the query has sth relevant/"enough" input for searching
              if (query.length() >= 2) {
                getServer()
                    .getView()
                    .getClasses()
                    .forEach(
                        clazz -> {
                          if (list.size() >= limit) {
                            return;
                          }

                          final SymbolCapabilities workspaceSymbol =
                              getServer().getClientCapabilities().getWorkspace().getSymbol();
                          if (workspaceSymbol == null) {
                            return;
                          }
                          final SymbolKindCapabilities symbolKind = workspaceSymbol.getSymbolKind();
                          if (symbolKind == null) {
                            return;
                          }

                          final SignaturePositionResolver signaturePositionResolver =
                              ((JimpleTextDocumentService) getServer().getTextDocumentService())
                                  .getSignaturePositionResolver(Util.classToUri(clazz));
                          /* FIXME; JimpleSymbolProvider.retrieveAndFilterSymbolsFromClass(
                              list,
                              query,
                              (SootClass) clazz,
                              signaturePositionResolver,
                              symbolKind,
                              limit); */
                        });
              }
              return Either.forLeft(list);
            });
  }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams didChangeConfigurationParams) {

    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams didChangeWatchedFilesParams) {

    }
}
