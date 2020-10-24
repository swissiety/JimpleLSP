package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.frontend.AbstractClassSource;
import de.upb.swt.soot.core.model.AbstractClass;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.jimple.parser.JimpleConverter;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.MagpieTextDocumentService;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author Markus Schmidt
 */
public class JimpleTextDocumentService extends MagpieTextDocumentService {
  /**
   * Instantiates a new magpie text document service.
   *
   * @param server the server
   */
  public JimpleTextDocumentService(MagpieServer server) {
    super(server);
  }

  JimpleLspServer getServer() {
    return (JimpleLspServer) server;
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    super.didChange(params);
    // FIXME: analyze in quarantine and add to a new view
  }

  @Override
  public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
    // TODO implement: go to declaration of Local/Field- if position resolves to a Type -> go to typeDefinition
    return null;
  }

  @Override
  public CompletableFuture<List<? extends Location>> typeDefinition(TextDocumentPositionParams position) {
    // TODO: resolve position to: Variable/FieldSignature/MethodSignature/TypeSignature - get its type -> return its definition position
    return null;
  }

  @Override
  public CompletableFuture<List<? extends Location>> implementation(TextDocumentPositionParams position) {
    // TODO implement: resolve position to MethodSignature/Type and retrieve respective subclasses
    return null;
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    // TODO: find usages of methods|ClassType|(Locals?)
    return null;
  }

  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
    return getServer().pool(() -> {

      // TODO implement retrieving symbols
      final TextDocumentClientCapabilities textDocument = getServer().getClientCapabilities().getTextDocument();
      if (textDocument == null) {
        return null;
      }
      final DocumentSymbolCapabilities documentSymbol = textDocument.getDocumentSymbol();
      if (documentSymbol == null) {
        return null;
      }

      final SymbolKindCapabilities symbolKind = documentSymbol.getSymbolKind();
      if (symbolKind == null) {
        return null;
      }

      final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass = getServer().getView().getClass(getServer().docIdentifierToClazz(params.getTextDocument()));
      if (!aClass.isPresent()) {
        return null;
      }
      SootClass clazz = (SootClass) aClass.get();
      List<Either<SymbolInformation, DocumentSymbol>> resultList = new ArrayList<>();
      List<SymbolInformation> list = new ArrayList<>();
      JimpleSymbolProvider.retrieveAndFilterSymbolsFromClass(list, "", clazz, symbolKind);
      list.forEach(s -> resultList.add(Either.forLeft(s)));
      return resultList;
    });
  }


}
