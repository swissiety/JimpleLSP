package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.frontend.AbstractClassSource;
import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.model.*;
import de.upb.swt.soot.core.signatures.FieldSignature;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.signatures.Signature;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.jimple.JimpleLexer;
import de.upb.swt.soot.jimple.JimpleParser;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.MagpieTextDocumentService;
import magpiebridge.jimplelsp.provider.JimpleSymbolProvider;
import magpiebridge.jimplelsp.provider.JimpleLabelLinkProvider;
import org.antlr.v4.runtime.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author Markus Schmidt
 */
public class JimpleTextDocumentService extends MagpieTextDocumentService {
  private Map<String, SignaturePositionResolver> docPosResolver = new HashMap<>();

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
    docPosResolver.put(params.getTextDocument().getUri(), new SignaturePositionResolver(params.getTextDocument().getUri(), params.getTextDocument().getText()));
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
          return Util.positionToLocation(Util.pathToUri(sc.getClassSource().getSourcePath()), sc.getPosition());
        }

      } else if (sig instanceof MethodSignature) {
        final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass = getServer().getView().getClass(((MethodSignature) sig).getDeclClassType());
        if (aClass.isPresent()) {
          SootClass sc = (SootClass) aClass.get();
          final Optional<SootMethod> method = sc.getMethod(((MethodSignature) sig));
          if (method.isPresent()) {
            return Util.positionToLocation(Util.pathToUri(sc.getClassSource().getSourcePath()), method.get().getPosition());
          }
        }

      } else if (sig instanceof FieldSignature) {
        final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass = getServer().getView().getClass(((FieldSignature) sig).getDeclClassType());
        if (aClass.isPresent()) {
          SootClass sc = (SootClass) aClass.get();
          final Optional<SootField> field = sc.getField(((FieldSignature) sig).getSubSignature());
          if (field.isPresent()) {
            return Util.positionToLocation(Util.pathToUri(sc.getClassSource().getSourcePath()), field.get().getPosition());
          }
        }

      }

      return null;
    });
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TextDocumentPositionParams position) {
    // TODO: resolve position to: Variable/FieldSignature/MethodSignature/TypeSignature - get its type -> return its definition position
    return getServer().pool(() -> {
      return null;
    });
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> declaration(TextDocumentPositionParams params) {
    // TODO: find local declaration
    return getServer().pool(() -> {
      String fileUri = params.getTextDocument().getUri();

      // TODO: getClass - getMethods; find Method surrounding
      params.getPosition();
      // TODO: parse usages of local in this method -> return declarations ; class: LocalDeclarationResolver
      /*
      try {
        JimpleLexer lexer = new JimpleLexer(CharStreams.fromPath(Paths.get(fileUri)));
        TokenStream tokens = new CommonTokenStream(lexer);
        JimpleParser parser = new JimpleParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
          public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            throw new ResolveException("Jimple Syntaxerror: " + msg, fileUri, new de.upb.swt.soot.core.model.Position(line, charPositionInLine, -1, -1));
          }
        });

        final JimpleDocumentPositionResolver.SignatureOccurenceAggregator signatureOccurenceAggregator = new JimpleDocumentPositionResolver.SignatureOccurenceAggregator();
        parser.file().enterRule(signatureOccurenceAggregator);

      } catch (IOException exception) {
        exception.printStackTrace();
      }*/
      return null;
    });
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(TextDocumentPositionParams position) {
    // TODO implement: resolve position to MethodSignature/Type and retrieve respective subclasses
    return getServer().pool(() -> {
      return null;
    });
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    // TODO: find usages of methods|ClassType|(Locals?)
    return getServer().pool(() -> {
      return null;
    });
  }


  @Override
  public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
    // make labels clickable to get to the label
    return getServer().pool(() -> {

      try {
        final String fileUri = params.getTextDocument().getUri();
        JimpleLexer lexer = new JimpleLexer(CharStreams.fromPath(Paths.get(fileUri)));
        TokenStream tokens = new CommonTokenStream(lexer);
        JimpleParser parser = new JimpleParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
          public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            throw new ResolveException("Jimple Syntaxerror: " + msg, fileUri, new de.upb.swt.soot.core.model.Position(line, charPositionInLine, -1, -1));
          }
        });

        final JimpleLabelLinkProvider listener = new JimpleLabelLinkProvider();
        parser.file().enterRule(listener);

        return listener.getLinks(fileUri);
      } catch (IOException exception) {
        exception.printStackTrace();
      }

      return null;
    });
  }

/*
  @Override
  public CompletableFuture<TypeHierarchyItem> resolveTypeHierarchy(ResolveTypeHierarchyItemParams params) {
    // TODO:
    return getServer().pool(() -> {
      params.getDirection();
      params.getItem();
      params.getResolve();
      return null;
    });
  }

  @Override
  public CompletableFuture<TypeHierarchyItem> typeHierarchy(TypeHierarchyParams params) {
    // TODO:
    return getServer().pool(() -> {
      params.getDirection();
      params.getResolve();
      return null;
    });
  }

  @Override
  public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
    // TODO: implement it for local usage
    return getServer().pool(() -> {
      position.getTextDocument().getUri();

      return null;
    });
  }
*/

  @Override
  public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
    return getServer().pool(() -> {

      final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass = getServer().getView().getClass(Util.uritoClasstype(params.getTextDocument().getUri()));
      if (aClass.isPresent()) {
        SootClass sc = (SootClass) aClass.get();
        List<FoldingRange> frList = new ArrayList<>();
        sc.getMethods().forEach(m -> {
          final FoldingRange fr = new FoldingRange(m.getPosition().getFirstLine(), m.getPosition().getLastLine());
          fr.setKind("region");
          frList.add(fr);
        });
        return frList;
      }

      // fold imports
      // fold multiline comments

      return null;
    });
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
