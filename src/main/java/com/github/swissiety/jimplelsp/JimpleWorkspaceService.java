package com.github.swissiety.jimplelsp;

import com.github.swissiety.jimplelsp.provider.JimpleSymbolProvider;
import com.github.swissiety.jimplelsp.resolver.SignaturePositionResolver;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.MagpieWorkspaceService;
import org.eclipse.lsp4j.SymbolCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKindCapabilities;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import sootup.core.model.SootClass;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** @author Markus Schmidt */
public class JimpleWorkspaceService extends MagpieWorkspaceService {
  public JimpleWorkspaceService(MagpieServer server) {
    super(server);
  }

  JimpleLspServer getServer() {
    return (JimpleLspServer) server;
  }

  @Override
  public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
    return getServer()
        .pool(
            () -> {
              int limit = 32;
              List<SymbolInformation> list = new ArrayList<>(limit);

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
                                  .getSignaturePositionResolver(Util.classToUri((SootClass) clazz));
                          JimpleSymbolProvider.retrieveAndFilterSymbolsFromClass(
                              list,
                              query,
                              (SootClass) clazz,
                              signaturePositionResolver,
                              symbolKind,
                              limit);
                        });
              }
              return list;
            });
  }
}
