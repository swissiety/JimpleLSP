package magpiebridge.jimplelsp;

import de.upb.swt.soot.callgraph.typehierarchy.ViewTypeHierarchy;
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
import de.upb.swt.soot.core.types.Type;
import de.upb.swt.soot.core.util.printer.Printer;
import de.upb.swt.soot.core.views.View;
import de.upb.swt.soot.jimple.JimpleLexer;
import de.upb.swt.soot.jimple.JimpleParser;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.MagpieTextDocumentService;
import magpiebridge.file.SourceFileManager;
import magpiebridge.jimplelsp.provider.JimpleSymbolProvider;
import magpiebridge.jimplelsp.resolver.LocalResolver;
import magpiebridge.jimplelsp.resolver.SignaturePositionResolver;
import org.antlr.v4.runtime.*;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

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
  JimpleParser createParserForUri(Path fileUri) throws IOException {
    JimpleLexer lexer = new JimpleLexer(CharStreams.fromPath(fileUri));
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
    if (params == null || params.getTextDocument() == null) {
      return;
    }
    final String uri = params.getTextDocument().getUri();
    if (uri == null) {
      return;
    }
    final String text = params.getTextDocument().getText();
    if (text == null) {
      return;
    }

    analyzeFile(uri, text);
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    super.didSave(params);

    if (params == null || params.getTextDocument() == null) {
      return;
    }
    final String uri = params.getTextDocument().getUri();
    if (uri == null) {
      return;
    }
    final String text = params.getText();
    if (text == null) {
      return;
    }
    // update classes
    analyzeFile(uri, text);
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    super.didClose(params);
    if (params == null
        || params.getTextDocument() == null
        || params.getTextDocument().getUri() == null) {
      return;
    }
    // clear position cache
    docSignaturePositionResolver.remove(params.getTextDocument().getUri());
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    super.didChange(params);

    final String uri = params.getTextDocument().getUri();
    String language = inferLanguage(uri);
    SourceFileManager fileManager = server.getSourceFileManager(language);
    getServer()
        .quarantineInputOrUpdate(
            params.getTextDocument().getUri(), fileManager.getVersionedFiles().get(uri).getText());
  }

  private void analyzeFile(@Nonnull String uri, @Nonnull String text) {
    final boolean valid = getServer().quarantineInputOrUpdate(uri, text);
    if (valid) {
      // calculate and cache interesting i.e.signature positions of the opened file
      docSignaturePositionResolver.put(
          uri, new SignaturePositionResolver(Util.uriToPath(uri), text));
    }
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      definition(TextDocumentPositionParams position) {
    if (position == null) {
      return null;
    }

    //  go to declaration of Field/Method/Class/Local - if position resolves to a Type -> go to
    // typeDefinition
    return getServer()
        .pool(
            () -> {
              final String uri = position.getTextDocument().getUri();
              final SignaturePositionResolver resolver = docSignaturePositionResolver.get(uri);
              if (resolver == null) {
                return null;
              }
              final Pair<Signature, Range> sigInst = resolver.resolve(position.getPosition());
              if (sigInst == null) {
                // try whether its a Local (which has no Signature!)

                final ClassType classType = getServer().uriToClasstype(uri);
                if (classType == null) {
                  return null;
                }

                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass(classType);
                if (!aClass.isPresent()) {
                  return null;
                }
                SootClass sc = (SootClass) aClass.get();

                // maybe: cache instance for this file like for sigs
                final LocalResolver localResolver = new LocalResolver(Util.uriToPath(uri));
                return localResolver.resolveDefinition(sc, position);
              }
              Signature sig = sigInst.getLeft();

              if (sig instanceof ClassType) {
                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass((ClassType) sig);
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  return Util.positionToLocationList(
                      Util.pathToUri(sc.getClassSource().getSourcePath()), sc.getPosition());
                }

              } else if (sig instanceof MethodSignature) {
                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass(((MethodSignature) sig).getDeclClassType());
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  final Optional<SootMethod> method = sc.getMethod(((MethodSignature) sig));
                  if (method.isPresent()) {
                    return Util.positionToLocationList(
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
                    return Util.positionToLocationList(
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
      implementation(TextDocumentPositionParams position) {
    if (position == null) {
      return null;
    }
    // resolve position to ClassSignature/MethodSignature and retrieve respective
    // subclasses/overriding methods there
    return getServer()
        .pool(
            () -> {
              List<Location> list = new ArrayList<>();

              final String uri = position.getTextDocument().getUri();
              final SignaturePositionResolver sigResolver = docSignaturePositionResolver.get(uri);
              if (sigResolver == null) {
                return null;
              }
              final Pair<Signature, Range> sigInstance =
                  sigResolver.resolve(position.getPosition());
              if (sigInstance == null) {
                return null;
              }
              Signature sig = sigInstance.getLeft();

              final View view = getServer().getView();
              final ViewTypeHierarchy typeHierarchy = new ViewTypeHierarchy(view);

              if (sig instanceof ClassType) {
                final Set<ClassType> classTypes = typeHierarchy.subtypesOf((ClassType) sig);

                classTypes.forEach(
                    csig -> {
                      Optional<SootClass> scOpt = (Optional<SootClass>) view.getClass(csig);
                      scOpt.ifPresent(
                          sootClass ->
                              list.add(Util.positionToLocation(Util.pathToUri(sootClass.getClassSource().getSourcePath()), sootClass.getPosition())));
                    });

                return Either.forLeft(list);
              } else if (sig instanceof MethodSignature) {
                final Set<ClassType> classTypes =
                    typeHierarchy.subtypesOf(((MethodSignature) sig).getDeclClassType());
                classTypes.forEach(
                    csig -> {
                      Optional<SootClass> scOpt = (Optional<SootClass>) view.getClass(csig);
                      if (scOpt.isPresent()) {
                        final SootClass sc = scOpt.get();
                        final Optional<SootMethod> methodOpt =
                            sc.getMethod(((MethodSignature) sig).getSubSignature());
                        final String methodsClassUri = Util.pathToUri(sc.getClassSource().getSourcePath());
                        methodOpt.ifPresent(
                            method ->
                            {
                              list.add(
                                  Util.positionToLocation(
                                          methodsClassUri, method.getPosition()));
                            });
                      }
                    });

                return Either.forLeft(list );
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
              final Pair<Signature, Range> sigInstance = resolver.resolve(params.getPosition());
              if (sigInstance == null) {
                return null;
              }
              Signature sig = sigInstance.getLeft();

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
                          // list a stmt just once even if the classtype occures multiple times in a
                          // single stmt.
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
                      } else {
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
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      typeDefinition(TextDocumentPositionParams position) {
    if (position == null) {
      return null;
    }

    // method-> returntype; field -> type; local -> type
    return getServer()
        .pool(
            () -> {
              final String uri = position.getTextDocument().getUri();
              final SignaturePositionResolver resolver = docSignaturePositionResolver.get(uri);
              if (resolver == null) {
                return null;
              }
              final Pair<Signature, Range> sigInst = resolver.resolve(position.getPosition());
              if (sigInst == null) {
                // try whether its a Local (which has no Signature!)
                final ClassType classType = getServer().uriToClasstype(uri);
                if (classType == null) {
                  return null;
                }

                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass(classType);
                if (!aClass.isPresent()) {
                  return null;
                }
                SootClass sc = (SootClass) aClass.get();

                // maybe: cache instance for this file like for sigs
                final LocalResolver localResolver = new LocalResolver(Util.uriToPath(uri));
                final Type type = localResolver.resolveTypeDefinition(sc, position);

                if (!(type instanceof ClassType)) {
                  return null;
                }
                final Optional<SootClass> typeClass =
                    (Optional<SootClass>) getServer().getView().getClass((ClassType) type);
                if (typeClass.isPresent()) {
                  final SootClass sootClass = typeClass.get();
                  return Util.positionToLocationList(
                      Util.pathToUri(sootClass.getClassSource().getSourcePath()),
                      sootClass.getPosition());
                }
                return null;
              }
              Signature sig = sigInst.getLeft();

              if (sig instanceof ClassType) {
                return null;
              } else if (sig instanceof MethodSignature) {
                final Type type = ((MethodSignature) sig).getType();
                if (!(type instanceof ClassType)) {
                  return null;
                }
                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass((ClassType) type);
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  final Optional<SootMethod> method = sc.getMethod(((MethodSignature) sig));
                  if (method.isPresent()) {
                    return Util.positionToLocationList(
                        Util.pathToUri(sc.getClassSource().getSourcePath()),
                        method.get().getPosition());
                  }
                }

              } else if (sig instanceof FieldSignature) {
                final Type type = ((FieldSignature) sig).getType();
                if (!(type instanceof ClassType)) {
                  return null;
                }
                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass((ClassType) type);
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  final Optional<SootField> field =
                      sc.getField(((FieldSignature) sig).getSubSignature());
                  if (field.isPresent()) {
                    return Util.positionToLocationList(
                        Util.pathToUri(sc.getClassSource().getSourcePath()),
                        field.get().getPosition());
                  }
                }
              }

              return null;
            });
  }

  @Override
  public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
    if (position == null) {
      return null;
    }

    return getServer()
        .pool(
            () -> {
              final String uri = position.getTextDocument().getUri();
              final SignaturePositionResolver sigResolver = docSignaturePositionResolver.get(uri);
              if (sigResolver == null) {
                return null;
              }
              final Pair<Signature, Range> sigInstance =
                  sigResolver.resolve(position.getPosition());
              if (sigInstance == null) {
                return null;
              }
              Signature sig = sigInstance.getLeft();

              String str = null;
              if (sig instanceof ClassType) {
                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass((ClassType) sig);
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  str = Modifier.toString(sc.getModifiers()) + " " + sc.toString();
                }
              } else if (sig instanceof MethodSignature) {
                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass(((MethodSignature) sig).getDeclClassType());
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  final Optional<SootMethod> aMethod =
                      sc.getMethod(((MethodSignature) sig).getSubSignature());
                  if (aMethod.isPresent()) {
                    final SootMethod sootMethod = aMethod.get();
                    str =
                        Modifier.toString(sootMethod.getModifiers()) + " " + sootMethod.toString();
                  }
                }
              } else if (sig instanceof FieldSignature) {
                final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                    getServer().getView().getClass(((FieldSignature) sig).getDeclClassType());
                if (aClass.isPresent()) {
                  SootClass sc = (SootClass) aClass.get();
                  final Optional<SootField> aField =
                      sc.getField(((FieldSignature) sig).getSubSignature());
                  if (aField.isPresent()) {
                    final SootField sootField = aField.get();
                    str = Modifier.toString(sootField.getModifiers()) + " " + sootField.toString();
                  }
                }
              }

              if (str != null) {
                return new Hover(
                    new MarkupContent(MarkupKind.PLAINTEXT, str), sigInstance.getRight());
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

  @Override
  public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(
      TextDocumentPositionParams position) {
    if (position == null) {
      return null;
    }

    // local references
    return getServer()
        .pool(
            () -> {
              final String uri = position.getTextDocument().getUri();
              final LocalResolver resolver = new LocalResolver(Util.uriToPath(uri));

              final ClassType classType = getServer().uriToClasstype(uri);
              if (classType == null) {
                return null;
              }
              final View view = getServer().getView();
              final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                  view.getClass(classType);
              if (aClass.isPresent()) {
                SootClass sc = (SootClass) aClass.get();
                return resolver.resolveReferences(sc, position);
              }

              return null;
            });
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    if (params == null) {
      return null;
    }
    final TextDocumentIdentifier textDocument = params.getTextDocument();
    if (textDocument == null) {
      return null;
    }
    final String uri = textDocument.getUri();
    if (uri == null) {
      return null;
    }

    return getServer()
        .pool(
            () -> {
              // warning: removes comments!
              final ClassType classType = getServer().uriToClasstype(uri);
              if (classType == null) {
                return null;
              }
              final View view = getServer().getView();
              final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                  view.getClass(classType);
              if (aClass.isPresent()) {
                SootClass sc = (SootClass) aClass.get();

                final StringWriter out = new StringWriter();
                PrintWriter writer = new PrintWriter(out);
                de.upb.swt.soot.core.util.printer.Printer printer = new Printer();
                printer.printTo(sc, writer);
                writer.close();
                final String newText = out.toString();
                return Collections.singletonList(
                    new TextEdit(
                        new Range(
                            new Position(0, 0),
                            new Position(
                                sc.getPosition().getLastLine(), sc.getPosition().getLastCol())),
                        newText));
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
              final ClassType classType =
                  getServer().uriToClasstype(params.getTextDocument().getUri());
              if (classType == null) {
                return null;
              }
              final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                  getServer().getView().getClass(classType);
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
              final TextDocumentClientCapabilities textDocumentCap =
                  getServer().getClientCapabilities().getTextDocument();
              if (textDocumentCap == null) {
                return null;
              }
              final DocumentSymbolCapabilities documentSymbol = textDocumentCap.getDocumentSymbol();
              if (documentSymbol == null) {
                return null;
              }
              final SymbolKindCapabilities symbolKind = documentSymbol.getSymbolKind();
              if (symbolKind == null) {
                return null;
              }
              final TextDocumentIdentifier textDoc = params.getTextDocument();
              if (textDoc == null) {
                return null;
              }
              final String uri = textDoc.getUri();
              if (uri == null) {
                return null;
              }
              final ClassType classType = getServer().uriToClasstype(uri);
              if (classType == null) {
                return null;
              }
              final Optional<? extends AbstractClass<? extends AbstractClassSource>> aClass =
                  getServer().getView().getClass(classType);
              if (!aClass.isPresent()) {
                return null;
              }

              SootClass clazz = (SootClass) aClass.get();
              List<SymbolInformation> list = new ArrayList<>();
              int limit = Integer.MAX_VALUE;
              JimpleSymbolProvider.retrieveAndFilterSymbolsFromClass(
                  list, null, clazz, symbolKind, limit);
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
