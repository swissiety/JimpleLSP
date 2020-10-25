package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.frontend.AbstractClassSource;
import de.upb.swt.soot.core.model.*;
import de.upb.swt.soot.core.signatures.FieldSignature;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.signatures.Signature;
import de.upb.swt.soot.core.types.ClassType;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.MagpieTextDocumentService;
import magpiebridge.jimplelsp.provider.JimpleSymbolProvider;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author Markus Schmidt
 */
public class JimpleTextDocumentService extends MagpieTextDocumentService {
  private Map<String, JimpleDocumentPositionResolver> docPosResolver = new HashMap<>();

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
  public void didOpen(DidOpenTextDocumentParams params) {
    super.didOpen(params);

    // calculate and cache positions
    docPosResolver.put(params.getTextDocument().getUri(), new JimpleDocumentPositionResolver(params.getTextDocument().getUri(), params.getTextDocument().getText()));
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    super.didClose(params);
    // clear position cache
    docPosResolver.remove(params.getTextDocument().getUri());
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    super.didChange(params);
    // FIXME: analyze in quarantine and add to a new view
    // getServer().quarantineInput(params.getTextDocument() )

  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(TextDocumentPositionParams position) {
    //  go to declaration of Local/Field- if position resolves to a Type -> go to typeDefinition
    return getServer().pool(() -> {
      final Signature sig = docPosResolver.get(position.getTextDocument().getUri()).resolve(position.getPosition());
      if (sig == null) {
        // here is nothing to resolve
        return null;
      }

      if (sig instanceof ClassType) {
        final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass = getServer().getView().getClass((ClassType) sig);
        if (aClass.isPresent()) {
          SootClass sc = (SootClass) aClass.get();
          return Util.positionToLocation(sc.getPosition());
        }

      } else if (sig instanceof MethodSignature) {
        final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass = getServer().getView().getClass(((MethodSignature) sig).getDeclClassType());
        if (aClass.isPresent()) {
          SootClass sc = (SootClass) aClass.get();
          final Optional<SootMethod> method = sc.getMethod(((MethodSignature) sig));
          if (method.isPresent()) {
            return Util.positionToLocation(method.get().getPosition());
          }
        }

      } else if (sig instanceof FieldSignature) {
        final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass = getServer().getView().getClass(((FieldSignature) sig).getDeclClassType());
        if (aClass.isPresent()) {
          SootClass sc = (SootClass) aClass.get();
          final Optional<SootField> field = sc.getField(((FieldSignature) sig).getSubSignature());
          if (field.isPresent()) {
            return Util.positionToLocation(field.get().getPosition());
          }
        }

      }

      return null;
    });
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TextDocumentPositionParams position) {
    // TODO: resolve position to: Variable/FieldSignature/MethodSignature/TypeSignature - get its type -> return its definition position


    return null;
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(TextDocumentPositionParams position) {
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

      final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass = getServer().getView().getClass(getServer().docIdentifierToClassType(params.getTextDocument()));
      if (!aClass.isPresent()) {
        return null;
      }

      SootClass clazz = (SootClass) aClass.get();
      List<SymbolInformation> list = new ArrayList<>();
      JimpleSymbolProvider.retrieveAndFilterSymbolsFromClass(list, "", clazz, symbolKind);
      List<Either<SymbolInformation, DocumentSymbol>> resultList = new ArrayList<>(list.size());
      list.forEach(s -> resultList.add(Either.forLeft(s)));
      return resultList;
    });
  }


}
