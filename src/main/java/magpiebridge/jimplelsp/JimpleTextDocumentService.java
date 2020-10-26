package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.frontend.AbstractClassSource;
import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.jimple.basic.Value;
import de.upb.swt.soot.core.jimple.common.expr.AbstractInvokeExpr;
import de.upb.swt.soot.core.jimple.common.ref.JFieldRef;
import de.upb.swt.soot.core.jimple.common.stmt.JAssignStmt;
import de.upb.swt.soot.core.jimple.common.stmt.JInvokeStmt;
import de.upb.swt.soot.core.jimple.common.stmt.Stmt;
import de.upb.swt.soot.core.model.*;
import de.upb.swt.soot.core.signatures.FieldSignature;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.signatures.Signature;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.util.printer.Printer;
import de.upb.swt.soot.jimple.JimpleLexer;
import de.upb.swt.soot.jimple.JimpleParser;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.MagpieTextDocumentService;
import magpiebridge.jimplelsp.provider.JimpleLabelLinkProvider;
import magpiebridge.jimplelsp.provider.JimpleSymbolProvider;
import org.antlr.v4.runtime.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import soot.jimple.InvokeExpr;

/** @author Markus Schmidt */
public class JimpleTextDocumentService extends MagpieTextDocumentService {
  private final Map<String, SignaturePositionResolver> docSignaturePositionResolver =
      new HashMap<>();

  /**
   * Instantiates a new magpie text document service.
   *
   * @param server the server
   */
  public JimpleTextDocumentService(@Nonnull MagpieServer server) {
    super(server);
  }

  @Nonnull
  JimpleLspServer getServer() {
    return (JimpleLspServer) server;
  }

  @Nonnull
  JimpleParser createParserForUri(String fileUri) throws IOException {
    JimpleLexer lexer = new JimpleLexer(CharStreams.fromPath(Paths.get(fileUri)));
    TokenStream tokens = new CommonTokenStream(lexer);
    JimpleParser parser = new JimpleParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(
        new BaseErrorListener() {
          public void syntaxError(
              Recognizer<?, ?> recognizer,
              Object offendingSymbol,
              int line,
              int charPositionInLine,
              String msg,
              RecognitionException e) {
            throw new ResolveException(
                "Jimple Syntaxerror: " + msg,
                fileUri,
                new de.upb.swt.soot.core.model.Position(line, charPositionInLine, -1, -1));
          }
        });
    return parser;
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    super.didOpen(params);
    if (params == null || params.getTextDocument() == null || params.getTextDocument().getUri() == null || params.getTextDocument().getText() == null) {
      return;
    }
    // calculate and cache positions
    docSignaturePositionResolver.put(
        params.getTextDocument().getUri(),
        new SignaturePositionResolver(
            params.getTextDocument().getUri(), params.getTextDocument().getText()));
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    super.didClose(params);
    if (params == null || params.getTextDocument() == null || params.getTextDocument().getUri() == null) {
      return;
    }
    // clear position cache
    docSignaturePositionResolver.remove(params.getTextDocument().getUri());
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    super.didChange(params);

    //FIXME: analyze in quarantine and add to a new view
    // getServer().quarantineInput(params.getTextDocument().getUri(), params.getContentChanges(). );
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      definition(TextDocumentPositionParams position) {
    if (position == null) {
      return null;
    }

    //  go to declaration of Local/Field- if position resolves to a Type -> go to typeDefinition
    return getServer()
        .pool(
            () -> {
              final SignaturePositionResolver resolver = docSignaturePositionResolver
                      .get(position.getTextDocument().getUri());
              if (resolver == null) {
                return null;
              }
              final Signature sig =
                  resolver
                      .resolve(position.getPosition());
              if (sig == null) {
                // here is nothing to resolve
                return null;
              }
              if (sig instanceof ClassType) {
                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass((ClassType) sig);
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  return Util.positionToLocation(
                      Util.pathToUri(sc.getClassSource().getSourcePath()), sc.getPosition());
                }

              } else if (sig instanceof MethodSignature) {
                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass(((MethodSignature) sig).getDeclClassType());
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  final Optional<SootMethod> method = sc.getMethod(((MethodSignature) sig));
                  if (method.isPresent()) {
                    return Util.positionToLocation(
                        Util.pathToUri(sc.getClassSource().getSourcePath()),
                        method.get().getPosition());
                  }
                }

              } else if (sig instanceof FieldSignature) {
                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass(((FieldSignature) sig).getDeclClassType());
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  final Optional<SootField> field =
                      sc.getField(((FieldSignature) sig).getSubSignature());
                  if (field.isPresent()) {
                    return Util.positionToLocation(
                        Util.pathToUri(sc.getClassSource().getSourcePath()),
                        field.get().getPosition());
                  }
                }
              }

              return null;
            });
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      typeDefinition(TextDocumentPositionParams position) {
    if (position == null) {
      return null;
    }
    // TODO: resolve position to: Variable/FieldSignature/MethodSignature/TypeSignature - get its
    // type -> return its definition position
    return getServer()
        .pool(
            () -> {
              return null;
            });
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      declaration(TextDocumentPositionParams params) {
    if (params == null) {
      return null;
    }
    // TODO: find local declaration
    return getServer()
        .pool(
            () -> {
              String fileUri = params.getTextDocument().getUri();

              // TODO: getClass - getMethods; find Method surrounding
              params.getPosition();
              // TODO: parse usages of local in this method -> return declarations ; class:
              // LocalDeclarationResolver

              try {
                JimpleParser parser = createParserForUri(fileUri);
                //        parser.file().enterRule(signatureOccurenceAggregator);

              } catch (IOException exception) {
                exception.printStackTrace();
              }

              return null;
            });
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      implementation(TextDocumentPositionParams position) {
    if (position == null) {
      return null;
    }
    // TODO implement: resolve position to MethodSignature/Type and retrieve respective subclasses
    return getServer()
        .pool(
            () -> {
              List<Location> list = new ArrayList<>();
              final SignaturePositionResolver resolver =
                  docSignaturePositionResolver.get(position.getTextDocument().getUri());
              if (resolver == null) {
                return null;
              }
              final Signature sig = resolver.resolve(position.getPosition());
              if (sig == null) {
                return null;
              }

              final Collection<SootClass> classes =
                  (Collection<SootClass>) getServer().getView().getClasses();
              if (sig instanceof ClassType) {
                // TODO
                for (SootClass sc : classes) {
                  for (SootMethod method : sc.getMethods()) {
                    final Body body = method.getBody();
                    for (Stmt stmt : body.getStmtGraph().nodes()) {
                      for (Value usesAndDef : stmt.getUsesAndDefs()) {
                        if (usesAndDef instanceof JFieldRef
                            && ((JFieldRef) usesAndDef).getFieldSignature().equals(sig)) {
                          list.add(
                              new Location(
                                  Util.classToUri(sc),
                                  Util.positionToRange(stmt.getPositionInfo().getStmtPosition())));
                        }
                      }
                    }
                  }
                }
              } else if (sig instanceof MethodSignature) {

                // TODO:
                for (SootClass sc : classes) {
                  for (SootMethod method : sc.getMethods()) {
                    final Body body = method.getBody();
                    for (Stmt stmt : body.getStmtGraph().nodes()) {
                      for (Value usesAndDef : stmt.getUsesAndDefs()) {
                        if (usesAndDef instanceof JFieldRef
                            && ((JFieldRef) usesAndDef).getFieldSignature().equals(sig)) {
                          list.add(
                              new Location(
                                  Util.classToUri(sc),
                                  Util.positionToRange(stmt.getPositionInfo().getStmtPosition())));
                        }
                      }
                    }
                  }
                }
              }

              return null;
            });
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    if (params == null) {
      return null;
    }
    // find usages of Methods|Classes
    return getServer()
        .pool(
            () -> {
              List<Location> list = new ArrayList<>();
              final SignaturePositionResolver resolver =
                  docSignaturePositionResolver.get(params.getTextDocument().getUri());
              if (resolver == null) {
                return null;
              }
              final Signature sig = resolver.resolve(params.getPosition());
              if (sig == null) {
                return null;
              }

              boolean includeDef =
                  params.getContext() != null && params.getContext().isIncludeDeclaration();
              if (sig instanceof ClassType) {
                Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass(((ClassType) sig));
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  list.add(
                      new Location(Util.classToUri(sc), Util.positionToRange(sc.getPosition())));
                }
              } else if (sig instanceof MethodSignature) {
                Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass(((MethodSignature) sig).getDeclClassType());
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  final Optional<? extends Method> method = sc.getMethod(((MethodSignature) sig));
                  method.ifPresent(
                      value ->
                          list.add(
                              new Location(
                                  Util.classToUri(sc),
                                  Util.positionToRange(((SootMethod) value).getPosition()))));
                }
              } else if (includeDef) {
                if (sig instanceof FieldSignature) {
                  Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                      getServer().getView().getClass(((FieldSignature) sig).getDeclClassType());
                  if (aClass.isPresent()) {
                    SootClass sc = (SootClass) aClass.get();
                    final Optional<? extends Field> field = sc.getField(((FieldSignature) sig));
                    field.ifPresent(
                        value ->
                            list.add(
                                new Location(
                                    Util.classToUri(sc),
                                    Util.positionToRange(((SootField) value).getPosition()))));
                  }
                }
              }

              final Collection<SootClass> classes =
                  (Collection<SootClass>) getServer().getView().getClasses();
              if (sig instanceof ClassType) {
                for (SootClass sc : classes) {
                  for (SootMethod method : sc.getMethods()) {
                    final Body body = method.getBody();
                    for (Stmt stmt : body.getStmtGraph().nodes()) {
                      for (Value usesAndDef : stmt.getUsesAndDefs()) {
                        if (usesAndDef.getType() == sig) {
                          list.add(
                              new Location(
                                  Util.classToUri(sc),
                                  Util.positionToRange(stmt.getPositionInfo().getStmtPosition())));
                          // list a stmt just once even if the classtype occures multiple times in a single stmt.
                          break;
                        }
                      }
                    }
                  }
                }
              } else if (sig instanceof FieldSignature) {
                for (SootClass sc : classes) {
                  for (SootMethod method : sc.getMethods()) {
                    final Body body = method.getBody();
                    for (Stmt stmt : body.getStmtGraph().nodes()) {
                      for (Value usesAndDef : stmt.getUsesAndDefs()) {
                        if (usesAndDef instanceof JFieldRef
                            && ((JFieldRef) usesAndDef).getFieldSignature().equals(sig)) {
                          list.add(
                              new Location(
                                  Util.classToUri(sc),
                                  Util.positionToRange(stmt.getPositionInfo().getStmtPosition())));
                        }
                      }
                    }
                  }
                }
              } else if (sig instanceof MethodSignature) {
                for (SootClass sc : classes) {
                  for (SootMethod method : sc.getMethods()) {
                    final Body body = method.getBody();
                    for (Stmt stmt : body.getStmtGraph().nodes()) {
                      AbstractInvokeExpr invokeExpr;
                      if (stmt instanceof JInvokeStmt) {
                        invokeExpr = stmt.getInvokeExpr();
                      } else if (stmt instanceof JAssignStmt
                              && ((JAssignStmt) stmt).getRightOp() instanceof AbstractInvokeExpr) {
                        invokeExpr = (AbstractInvokeExpr) ((JAssignStmt) stmt).getRightOp();
                      }else{
                        continue;
                      }

                      if (invokeExpr.getMethodSignature().equals(sig)) {
                        list.add(
                                new Location(
                                        Util.classToUri(sc),
                                        Util.positionToRange(stmt.getPositionInfo().getStmtPosition())));

                      }
                    }
                  }
                }
              }
              return list;
            });
  }

  @Override
  public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
    if (params == null) {
      return null;
    }
    // make labels clickable to get to the label
    return getServer()
        .pool(
            () -> {
              try {
                final String fileUri = params.getTextDocument().getUri();
                JimpleParser parser = createParserForUri(fileUri);

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
        if( params == null ){
        return null;
      }

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
        if( params == null ){
        return null;
      }

      // TODO:
      return getServer().pool(() -> {
        params.getDirection();
        params.getResolve();
        return null;
      });
    }
*/
  /*
    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
        if( params == null ){
        return null;
      }

      // TODO: implement it for local usage | signatures | ?
      return getServer().pool(() -> {
        position.getTextDocument().getUri();

        return null;
      });
    }
  */

  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    if (params == null) {
      return null;
    }

    return getServer()
        .pool(
            () -> {
              // warning: removes comments!
              final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass = getServer().getView().getClass(Util.uritoClasstype(params.getTextDocument().getUri()));
              if (aClass.isPresent()) {
                SootClass sc = (SootClass) aClass.get();

                final StringWriter out = new StringWriter();
                PrintWriter writer = new PrintWriter(out);
                de.upb.swt.soot.core.util.printer.Printer printer = new Printer();
                printer.printTo(sc, writer);
                writer.close();

                return Collections.singletonList(new TextEdit(new Range(new Position(0,0), new Position(sc.getPosition().getLastLine(),sc.getPosition().getLastCol())), out.toString() ));
              }
              return null;
            });
  }

  @Override
  public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
    if (params == null) {
      return null;
    }

    return getServer()
        .pool(
            () -> {
              final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                  getServer()
                      .getView()
                      .getClass(Util.uritoClasstype(params.getTextDocument().getUri()));
              if (aClass.isPresent()) {
                SootClass sc = (SootClass) aClass.get();
                List<FoldingRange> frList = new ArrayList<>();
                sc.getMethods()
                    .forEach(
                        m -> {
                          final FoldingRange fr =
                              new FoldingRange(
                                  m.getPosition().getFirstLine(), m.getPosition().getLastLine());
                          fr.setKind("region");
                          frList.add(fr);
                        });
                return frList;
              }

              // possibilities: fold imports | fold multiline comments
              return null;
            });
  }

  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
      DocumentSymbolParams params) {
    if (params == null) {
      return null;
    }

    return getServer()
        .pool(
            () -> {
              final TextDocumentClientCapabilities textDocument =
                  getServer().getClientCapabilities().getTextDocument();
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

              final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                  getServer()
                      .getView()
                      .getClass(getServer().docIdentifierToClassType(params.getTextDocument()));
              if (!aClass.isPresent()) {
                return null;
              }

              SootClass clazz = (SootClass) aClass.get();
              List<SymbolInformation> list = new ArrayList<>();
              JimpleSymbolProvider.retrieveAndFilterSymbolsFromClass(list, "", clazz, symbolKind);
              List<Either<SymbolInformation, DocumentSymbol>> resultList =
                  new ArrayList<>(list.size());
              list.forEach(s -> resultList.add(Either.forLeft(s)));
              return resultList;
            });
  }

  @Override
  protected String inferLanguage(String uri) {
    if (uri.endsWith(".jimple")) {
      return "jimple";
    }
    return super.inferLanguage(uri);
  }

}
