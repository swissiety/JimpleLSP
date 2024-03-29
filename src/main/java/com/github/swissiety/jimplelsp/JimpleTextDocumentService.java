package com.github.swissiety.jimplelsp;

import com.github.swissiety.jimplelsp.provider.JimpleSymbolProvider;
import com.github.swissiety.jimplelsp.provider.SyntaxHighlightingProvider;
import com.github.swissiety.jimplelsp.resolver.LocalPositionResolver;
import com.github.swissiety.jimplelsp.resolver.SignaturePositionResolver;
import com.github.swissiety.jimplelsp.workingtree.WorkingTree;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import sootup.core.model.*;
import sootup.core.signatures.FieldSignature;
import sootup.core.signatures.MethodSignature;
import sootup.core.signatures.Signature;
import sootup.core.typehierarchy.ViewTypeHierarchy;
import sootup.core.types.ClassType;
import sootup.core.types.Type;
import sootup.core.util.printer.JimplePrinter;
import sootup.core.views.View;
import sootup.jimple.JimpleParser;
import sootup.jimple.parser.JimpleConverterUtil;
import sootup.jimple.parser.JimpleView;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/** @author Markus Schmidt */
public class JimpleTextDocumentService implements TextDocumentService {

  WorkingTree workingTree = new WorkingTree("jimple");

  private final Map<Path, SignaturePositionResolver> docSignaturePositionResolver = new HashMap<>();

  private final Map<Path, JimpleParser> docParseTree = new HashMap<>();
  @Nonnull
  private final JimpleLspServer server;

  /**
   * Instantiates a new magpie text document service.
   *
   * @param server the server
   */
  public JimpleTextDocumentService(@Nonnull JimpleLspServer server) {
    this.server = server;
  }

  @Nonnull
  JimpleLspServer getServer() {
    return server;
  }

  /** TODO: refactor into magpiebridge */
  protected void forwardException(@Nonnull Exception e) {
    getServer()
        .getClient()
        .logMessage(new MessageParams(MessageType.Error, JimpleLspServer.getStringFrom(e)));
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
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
    workingTree.didOpen(params);
    analyzeFile(uri, text);
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    final String uri = params.getTextDocument().getUri();

    workingTree.didChange(params);
    analyzeFile(params.getTextDocument().getUri(), workingTree.get(uri).getContent());
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
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
    workingTree.didSave(params);

    // update classes
    analyzeFile(uri, text);
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    TextDocumentIdentifier textDocument = params.getTextDocument();
    if (textDocument == null || textDocument.getUri() == null) {
      return;
    }

    docParseTree.remove(Util.uriToPath(textDocument.getUri()));
  }

  private void analyzeFile(@Nonnull String uri, @Nonnull String text) {
    final boolean valid = getServer().quarantineInputOrUpdate(uri, text);
    Path path = Util.uriToPath(uri);
    if (valid) {
      // calculate and cache interesting i.e.signature positions of the opened file
      JimpleParser jimpleParser =
          JimpleConverterUtil.createJimpleParser(CharStreams.fromString(text), path);
      ParseTree parseTree = jimpleParser.file();
      docParseTree.put(path, jimpleParser);

      SignaturePositionResolver sigposresolver = new SignaturePositionResolver(path, parseTree);
      docSignaturePositionResolver.put(path, sigposresolver);
    } else {
      // file is invalid Jimple -> clear cache
      docParseTree.remove(path);
      docSignaturePositionResolver.remove(path);
    }
  }

  /*
  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      declaration(DeclarationParams params) {
    // TODO: handle locals different if local declarations are present
    // TODO: handle abstract method declarations
    // otherwise its equal to definition
    return definition(params);
  }
  */

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      definition(DefinitionParams position) {
    if (position == null) {
      return null;
    }

    //  go to definition of Field/Method/Class/Local - if position resolves to a Type -> go to
    // typeDefinition
    return getServer()
        .pool(
            () -> {
              final String uri = position.getTextDocument().getUri();
              final SignaturePositionResolver resolver = getSignaturePositionResolver(uri);
              if (resolver == null) {
                return null;
              }
              final Pair<Signature, Range> sigInst = resolver.resolve(position.getPosition());
              if (sigInst == null) {
                // try if its a Local (which has no Signature!)

                final ClassType classType = getServer().uriToClasstype(uri);
                if (classType == null) {
                  return null;
                }

                final Optional<SootClass<?>> aClass = getServer().getView().getClass(classType);
                if (!aClass.isPresent()) {
                  return null;
                }
                SootClass<?> sc = aClass.get();

                // maybe: cache instance for this file like for sigs
                Path path = Util.uriToPath(uri);
                final JimpleParser parser = getParser(path);
                if (parser == null) {
                  return null;
                }
                ParseTree parseTree = parser.file();
                if (parseTree == null) {
                  return null;
                }
                final LocalPositionResolver localPositionResolver =
                    new LocalPositionResolver(path, parseTree);
                LocationLink locationLink = localPositionResolver.resolveDefinition(sc, position);
                if (locationLink == null) {
                  return null;
                }
                if (getServer()
                        .getClientCapabilities()
                        .getTextDocument()
                        .getDefinition()
                        .getLinkSupport()
                    == Boolean.TRUE) {
                  return Either.forRight(Collections.singletonList(locationLink));
                } else {
                  return Either.forLeft(
                      Collections.singletonList(
                          new Location(
                              locationLink.getTargetUri(), locationLink.getTargetRange())));
                }
              }
              Signature sig = sigInst.getLeft();
              if (sig != null) {
                Location definitionLocation = getDefinitionLocation(sig);
                if (definitionLocation == null) {
                  return null;
                }

                if (getServer()
                        .getClientCapabilities()
                        .getTextDocument()
                        .getDefinition()
                        .getLinkSupport()
                    == Boolean.TRUE) {
                  return Either.forRight(
                      Collections.singletonList(
                          new LocationLink(
                              definitionLocation.getUri(),
                              definitionLocation.getRange(),
                              definitionLocation.getRange(),
                              sigInst.getRight())));
                } else {
                  return Either.forLeft(Collections.singletonList(definitionLocation));
                }
              }
              return null;
            });
  }

  @Nullable
  private Location getDefinitionLocation(@Nonnull Signature sig) {
    if (sig instanceof ClassType) {
      final Optional<SootClass<?>> aClass = getServer().getView().getClass((ClassType) sig);
      if (aClass.isPresent()) {
        SootClass<?> sc = aClass.get();
        SignaturePositionResolver resolver =
            getSignaturePositionResolver(Util.pathToUri(sc.getClassSource().getSourcePath()));
        if (resolver == null) {
          return null;
        }
        return resolver.findFirstMatchingSignature(sc.getType(), sc.getPosition());
      }

    } else if (sig instanceof MethodSignature) {
      final Optional<SootClass<?>> aClass =
          getServer().getView().getClass(((MethodSignature) sig).getDeclClassType());
      if (aClass.isPresent()) {
        SootClass<?> sc = aClass.get();
        final Optional<? extends SootMethod> methodOpt =
            sc.getMethod((((MethodSignature) sig).getSubSignature()));
        if (methodOpt.isPresent()) {
          final SootMethod method = methodOpt.get();
          SignaturePositionResolver resolver =
              getSignaturePositionResolver(Util.pathToUri(sc.getClassSource().getSourcePath()));
          if (resolver == null) {
            return null;
          }
          return resolver.findFirstMatchingSignature(method.getSignature(), method.getPosition());
        }
      }

    } else if (sig instanceof FieldSignature) {
      final Optional<SootClass<?>> aClass =
          getServer().getView().getClass(((FieldSignature) sig).getDeclClassType());
      if (aClass.isPresent()) {
        SootClass<?> sc = aClass.get();
        final Optional<? extends SootField> field =
            sc.getField(((FieldSignature) sig).getSubSignature());
        if (field.isPresent()) {
          final SootField sf = field.get();
          SignaturePositionResolver resolver =
              getSignaturePositionResolver(Util.pathToUri(sc.getClassSource().getSourcePath()));
          if (resolver == null) {
            return null;
          }
          return resolver.findFirstMatchingSignature(sf.getSignature(), sf.getPosition());
        }
      }
    }
    return null;
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      implementation(ImplementationParams position) {
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
              final SignaturePositionResolver sigResolver = getSignaturePositionResolver(uri);
              if (sigResolver == null) {
                return null;
              }
              final Pair<Signature, Range> sigInstance =
                  sigResolver.resolve(position.getPosition());
              if (sigInstance == null) {
                return null;
              }
              Signature sig = sigInstance.getLeft();

              final View<SootClass<?>> view = getServer().getView();
              final ViewTypeHierarchy typeHierarchy = new ViewTypeHierarchy(view);

              if (sig instanceof ClassType) {
                final Set<ClassType> subClassTypes = typeHierarchy.subtypesOf((ClassType) sig);

                subClassTypes.forEach(
                    subClassSig -> {
                      Optional<SootClass<?>> scOpt = view.getClass(subClassSig);
                      scOpt.ifPresent(
                          sootClass ->
                              list.add(
                                  Util.positionToDefLocation(
                                      Util.pathToUri(sootClass.getClassSource().getSourcePath()),
                                      sootClass.getPosition())));
                    });

                if (getServer()
                        .getClientCapabilities()
                        .getTextDocument()
                        .getDefinition()
                        .getLinkSupport()
                    == Boolean.TRUE) {
                  return Either.forRight(
                      list.stream()
                          .map(
                              i ->
                                  new LocationLink(
                                      i.getUri(),
                                      i.getRange(),
                                      i.getRange(),
                                      sigInstance.getRight()))
                          .collect(Collectors.toList()));
                } else {
                  return Either.forLeft(list);
                }

              } else if (sig instanceof MethodSignature) {
                final Set<ClassType> classTypes =
                    typeHierarchy.subtypesOf(((MethodSignature) sig).getDeclClassType());
                classTypes.forEach(
                    csig -> {
                      Optional<SootClass<?>> scOpt = view.getClass(csig);
                      if (scOpt.isPresent()) {
                        final SootClass<?> sc = scOpt.get();
                        final Optional<? extends SootMethod> methodOpt =
                            sc.getMethod(((MethodSignature) sig).getSubSignature());
                        final String methodsClassUri =
                            Util.pathToUri(sc.getClassSource().getSourcePath());
                        methodOpt.ifPresent(
                            method ->
                                list.add(
                                    Util.positionToDefLocation(
                                        methodsClassUri, method.getPosition())));
                      }
                    });

                if (getServer()
                        .getClientCapabilities()
                        .getTextDocument()
                        .getDefinition()
                        .getLinkSupport()
                    == Boolean.TRUE) {
                  return Either.forRight(
                      list.stream()
                          .map(
                              i ->
                                  new LocationLink(
                                      i.getUri(),
                                      i.getRange(),
                                      i.getRange(),
                                      sigInstance.getRight()))
                          .collect(Collectors.toList()));
                } else {
                  return Either.forLeft(list);
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
    // find usages of FieldSignaturesy|MethodSignatures|Classtypes
    return getServer()
        .pool(
            () -> {
              List<Location> list = new ArrayList<>();
              final String uri = params.getTextDocument().getUri();
              final SignaturePositionResolver resolver = getSignaturePositionResolver(uri);
              if (resolver == null) {
                return null;
              }
              final Pair<Signature, Range> sigInstance = resolver.resolve(params.getPosition());

              if (sigInstance == null) {
                // maybe its a Local?

                final ClassType classType = getServer().uriToClasstype(uri);
                if (classType == null) {
                  return null;
                }
                final JimpleView view = getServer().getView();
                final Optional<SootClass<?>> aClass = view.getClass(classType);
                if (aClass.isPresent()) {
                  SootClass<?> sc = aClass.get();
                  Path path = Util.uriToPath(uri);
                  final JimpleParser parser = getParser(path);
                  if (parser == null) {
                    return null;
                  }
                  ParseTree parseTree = parser.file();
                  if (parseTree == null) {
                    return null;
                  }
                  final LocalPositionResolver localPositionResolver =
                      new LocalPositionResolver(path, parseTree);
                  list.addAll(localPositionResolver.resolveReferences(sc, params));
                  return list;
                }

                return null;
              }
              Signature sig = sigInstance.getLeft();

              boolean includeDef =
                  params.getContext() != null && params.getContext().isIncludeDeclaration();
              final Location definitionLocation = includeDef ? null : getDefinitionLocation(sig);

              final Collection<SootClass<?>> classes = getServer().getView().getClasses();
              for (SootClass<?> sc : classes) {
                final Path scPath = sc.getClassSource().getSourcePath();
                final SignaturePositionResolver sigresolver = getSignaturePositionResolver(scPath);
                if (sigresolver == null) {
                  continue;
                }
                final List<Location> resolvedList = sigresolver.resolve(sig);

                if (resolvedList != null) {
                  // remove definition if requested
                  if (!includeDef) {
                    resolvedList.removeIf(loc -> loc.equals(definitionLocation));
                  }
                  list.addAll(resolvedList);
                }
              }

              return list;
            });
  }

  @Nullable
  public SignaturePositionResolver getSignaturePositionResolver(@Nonnull String uri) {
    return getSignaturePositionResolver(Util.uriToPath(uri));
  }

  @Nullable
  private SignaturePositionResolver getSignaturePositionResolver(@Nonnull Path path) {
    return docSignaturePositionResolver.computeIfAbsent(
        path,
        k -> {
          try {
            final JimpleParser parser = getParser(path);
            if (parser == null) {
              return null;
            }
            ParseTree parseTree = parser.file();
            if (parseTree == null) {
              return null;
            }
            return new SignaturePositionResolver(path, parseTree);
          } catch (IllegalStateException e) {
            forwardException(e);
          }
          return null;
        });
  }

  /**
   * onlyValid: flag to specify whether a partial parsetree ie of invalid or incomplete jimple files
   * are mandatory
   */
  @Nullable
  private JimpleParser getParser(@Nonnull Path path) {
    return docParseTree.computeIfAbsent(
        path,
        k -> {
          try {
            return JimpleConverterUtil.createJimpleParser(CharStreams.fromPath(path), path);
          } catch (IOException e) {
            forwardException(e);
            return null;
          }
        });
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      typeDefinition(TypeDefinitionParams position) {
    if (position == null) {
      return null;
    }

    // method-> returntype; field -> type; local -> type
    return getServer()
        .pool(
            () -> {
              final String uri = position.getTextDocument().getUri();
              final SignaturePositionResolver resolver = getSignaturePositionResolver(uri);
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

                final Optional<SootClass<?>> aClass = getServer().getView().getClass(classType);
                if (!aClass.isPresent()) {
                  return null;
                }
                SootClass<?> sc = aClass.get();

                // maybe: cache instance for this file like for sigs
                Path path = Util.uriToPath(uri);
                final JimpleParser parser = getParser(path);
                if (parser == null) {
                  return null;
                }
                ParseTree parseTree = parser.file();
                if (parseTree == null) {
                  return null;
                }
                final LocalPositionResolver localPositionResolver =
                    new LocalPositionResolver(path, parseTree);
                final Type type =
                    localPositionResolver.resolveTypeDefinition(sc, position.getPosition());

                if (!(type instanceof ClassType)) {
                  return null;
                }
                final Optional<SootClass<?>> typeClass =
                    getServer().getView().getClass((ClassType) type);
                if (typeClass.isPresent()) {
                  final SootClass<?> sootClass = typeClass.get();
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
                final Optional<SootClass<?>> aClass =
                    getServer().getView().getClass((ClassType) type);
                if (aClass.isPresent()) {
                  SootClass<?> sc = aClass.get();
                  final Optional<? extends SootMethod> method =
                      sc.getMethod(((MethodSignature) sig).getSubSignature());
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
                final Optional<SootClass<?>> aClass =
                    getServer().getView().getClass((ClassType) type);
                if (aClass.isPresent()) {
                  SootClass<?> sc = aClass.get();
                  final Optional<? extends SootField> field =
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
  public CompletableFuture<Hover> hover(HoverParams position) {
    if (position == null) {
      return null;
    }

    return getServer()
        .pool(
            () -> {
              final String uri = position.getTextDocument().getUri();
              final SignaturePositionResolver sigResolver = getSignaturePositionResolver(uri);
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
                final Optional<SootClass<?>> aClass =
                    getServer().getView().getClass((ClassType) sig);
                if (aClass.isPresent()) {
                  SootClass<?> sc = aClass.get();
                  str = ClassModifier.toString(sc.getModifiers()) + " " + sc;
                  Optional<? extends ClassType> superclass = sc.getSuperclass();
                  if (superclass.isPresent()) {
                    str += "\n extends " + superclass.get();
                  }

                  Iterator<? extends ClassType> interfaceIt = sc.getInterfaces().iterator();
                  if (interfaceIt.hasNext()) {
                    str += " implements " + interfaceIt.next();
                    while (interfaceIt.hasNext()) {
                      str += ", " + interfaceIt.next();
                    }
                  }
                }
              } else if (sig instanceof MethodSignature) {
                final Optional<SootClass<?>> aClass =
                    getServer().getView().getClass(((MethodSignature) sig).getDeclClassType());
                if (aClass.isPresent()) {
                  SootClass<?> sc = aClass.get();
                  final Optional<? extends SootMethod> aMethod =
                      sc.getMethod(((MethodSignature) sig).getSubSignature());
                  if (aMethod.isPresent()) {
                    final SootMethod sootMethod = aMethod.get();
                    str = MethodModifier.toString(sootMethod.getModifiers()) + " " + sootMethod;
                  }
                }
              } else if (sig instanceof FieldSignature) {
                final Optional<SootClass<?>> aClass =
                    getServer().getView().getClass(((FieldSignature) sig).getDeclClassType());
                if (aClass.isPresent()) {
                  SootClass<?> sc = aClass.get();
                  final Optional<? extends SootField> aField =
                      sc.getField(((FieldSignature) sig).getSubSignature());
                  if (aField.isPresent()) {
                    final SootField sootField = aField.get();
                    str = FieldModifier.toString(sootField.getModifiers()) + " " + sootField;
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

  @Override
  public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(
      DocumentHighlightParams position) {
    if (position == null) {
      return null;
    }

    // local references
    return getServer()
        .pool(
            () -> {
              final String uri = position.getTextDocument().getUri();
              Path path = Util.uriToPath(uri);
              final JimpleParser parser = getParser(path);
              if (parser == null) {
                return null;
              }
              ParseTree parseTree = parser.file();
              if (parseTree == null) {
                return null;
              }

              final LocalPositionResolver resolver = new LocalPositionResolver(path, parseTree);

              final ClassType classType = getServer().uriToClasstype(uri);
              if (classType == null) {
                return null;
              }
              final JimpleView view = getServer().getView();
              final Optional<SootClass<?>> aClass = view.getClass(classType);
              if (aClass.isPresent()) {
                SootClass<?> sc = aClass.get();
                return resolver.resolveReferences(sc, position).stream()
                    .map(ref -> new DocumentHighlight(ref.getRange(), DocumentHighlightKind.Text))
                    .collect(Collectors.toList());
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
              final JimpleView view = getServer().getView();
              final Optional<SootClass<?>> aClass = view.getClass(classType);
              if (aClass.isPresent()) {
                SootClass<?> sc = aClass.get();

                final StringWriter out = new StringWriter();
                PrintWriter writer = new PrintWriter(out);
                sootup.core.util.printer.JimplePrinter printer = new JimplePrinter();
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
    // possibilities: fold imports | fold multiline comments
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
              final Optional<SootClass<?>> aClass = getServer().getView().getClass(classType);
              if (aClass.isPresent()) {
                SootClass<?> sc = aClass.get();
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
              final Optional<SootClass<?>> aClass = getServer().getView().getClass(classType);
              if (!aClass.isPresent()) {
                return null;
              }

              SootClass<?> sc = aClass.get();
              List<SymbolInformation> list = new ArrayList<>();
              int limit = Integer.MAX_VALUE;
              JimpleSymbolProvider.retrieveAndFilterSymbolsFromClass(
                  list, null, sc, getSignaturePositionResolver(uri), symbolKind, limit);

              return list.stream()
                  .<Either<SymbolInformation, DocumentSymbol>>map(Either::forLeft)
                  .collect(Collectors.toCollection(() -> new ArrayList<>(list.size())));
            });
  }

  @Nullable
  ParserRuleContext tryParse(
      @Nonnull JimpleParser jp, @Nonnull List<Function<JimpleParser, ParserRuleContext>> levels) {

    ParserRuleContext tree;
    for (Function<JimpleParser, ParserRuleContext> level : levels) {
      try {
        tree = level.apply(jp);
        if (jp.getNumberOfSyntaxErrors() > 0) {
          jp.reset();
          continue;
        }
      } catch (Exception e) {
        jp.reset();
        continue;
      }
      return tree;
    }

    return null;
  }

  @Override
  public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
    final TextDocumentIdentifier textDoc = params.getTextDocument();
    if (textDoc == null) {
      return null;
    }
    final Path path = Util.uriToPath(textDoc.getUri());
    return getServer()
        .pool(
            () -> {
              final JimpleParser parser = getParser(path);
              if (parser == null) {
                // e.g. file not found
                return null;
              }

              List<Function<JimpleParser, ParserRuleContext>> levels = new ArrayList<>();
              levels.add(JimpleParser::file);
              levels.add(JimpleParser::member);
              levels.add(JimpleParser::method_body);

              levels.add(JimpleParser::declarations);
              levels.add(JimpleParser::statements);
              levels.add(JimpleParser::trap_clauses);

              levels.add(JimpleParser::field_signature);
              levels.add(JimpleParser::method_signature);
              levels.add(JimpleParser::method_subsignature);
              levels.add(JimpleParser::immediate);

              final ParserRuleContext parseTree = tryParse(parser, levels);
              if (parseTree == null) {
                return null;
              }

              return SyntaxHighlightingProvider.paintbrush(parseTree);
            });
  }

  public JimpleParser getDocParseTree(Path path) {
    return docParseTree.computeIfAbsent(
        path,
        p -> {
          try {
            analyzeFile(Util.pathToUri(path), Arrays.toString(Files.readAllBytes(path)));
            return docParseTree.get(path);
          } catch (IOException e) {
            e.printStackTrace();
            return null;
          }
        });
  }

}
