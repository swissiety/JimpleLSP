package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.model.SootField;
import de.upb.swt.soot.core.model.SootMethod;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.MagpieWorkspaceService;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class JimpleWorkspaceService extends MagpieWorkspaceService {
  public JimpleWorkspaceService(MagpieServer server) {
    super(server);
  }

  JimpleLspServer getServer() {
    return (JimpleLspServer) server;
  }

  @Override
  public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
    return getServer().pool(() -> {

      int limit = 100;
      List<SymbolInformation> list = new ArrayList<>(limit);

      final String query = params.getQuery().trim().toLowerCase();

      if (query.length() > 1) {
        ((JimpleLspServer) server).getView().getClasses().forEach(clazz -> {
          if (list.size() >= limit) {
            return;
          }

          final SymbolCapabilities workspaceSymbol = getServer().getClientCapabilities().getWorkspace().getSymbol();
          if (workspaceSymbol == null) {
            return;
          }
          final SymbolKindCapabilities symbolKind = workspaceSymbol.getSymbolKind();
          if (symbolKind == null) {
            return;
          }

          JimpleSymbolProvider.retrieveAndFilterSymbolsFromClass(list, query, (SootClass) clazz, symbolKind);
        });
      }
      return list;
    });
  }

}
