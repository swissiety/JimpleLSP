package com.github.swissiety.jimplelsp;

import com.github.swissiety.jimplelsp.provider.JimpleSymbolProvider;
import com.github.swissiety.jimplelsp.provider.SyntaxHighlightingProvider;
import com.github.swissiety.jimplelsp.resolver.LocalPositionResolver;
import com.github.swissiety.jimplelsp.resolver.SignaturePositionResolver;
import de.upb.swt.soot.callgraph.typehierarchy.ViewTypeHierarchy;
import de.upb.swt.soot.core.model.Modifier;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.model.SootField;
import de.upb.swt.soot.core.model.SootMethod;
import de.upb.swt.soot.core.signatures.FieldSignature;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.signatures.Signature;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.types.Type;
import de.upb.swt.soot.core.util.printer.Printer;
import de.upb.swt.soot.core.views.View;
import de.upb.swt.soot.jimple.JimpleParser;
import de.upb.swt.soot.jimple.parser.JimpleConverterUtil;
import de.upb.swt.soot.jimple.parser.JimpleView;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.MagpieTextDocumentService;
import magpiebridge.file.SourceFileManager;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author Markus Schmidt
 */
public class JimpleTextDocumentService extends MagpieTextDocumentService {
    private final Map<Path, SignaturePositionResolver> docSignaturePositionResolver = new HashMap<>();

    private final Map<Path, ParseTree> docParseTree = new HashMap<>();

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

    /**
     * TODO: refactor into magpiebridge
     */
    protected void forwardException(@Nonnull Exception e) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(bos);
        e.printStackTrace(pw);
        getServer().getClient().logMessage(new MessageParams(MessageType.Error, bos.toString()));
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        // FIXME: [ms] make magpiebridge:SourceFileModule.getSuffix() protected or create a central/open
        // language->suffix allocation
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
    public void didChange(DidChangeTextDocumentParams params) {
        super.didChange(params);

        final String uri = params.getTextDocument().getUri();
        String language = inferLanguage(uri);
        SourceFileManager fileManager = server.getSourceFileManager(language);
        analyzeFile(
                params.getTextDocument().getUri(),
                fileManager.getVersionedFiles().get(URI.create(uri)).getText());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // FIXME: [ms] magpiebridge getsuffix() super.didSave(params);
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
        // FIXME: [ms] magpiebridge getsuffix() super.didSave(params);
        super.didClose(params);
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
            docParseTree.put(path, parseTree);

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

                                final Optional<SootClass<?>> aClass =
                                        getServer().getView().getClass(classType);
                                if (!aClass.isPresent()) {
                                    return null;
                                }
                                SootClass<?> sc = aClass.get();

                                // maybe: cache instance for this file like for sigs
                                Path path = Util.uriToPath(uri);
                                ParseTree parseTree = docParseTree.get(path);
                                if (parseTree == null) {
                                    return null;
                                }
                                final LocalPositionResolver localPositionResolver =
                                        new LocalPositionResolver(path, parseTree);
                                LocationLink locationLink = localPositionResolver.resolveDefinition(sc, position);
                                if (locationLink == null) {
                                    return null;
                                }
                                if (getServer().getClientCapabilities().getTextDocument().getDefinition().getLinkSupport() == Boolean.TRUE) {
                                    return Either.forRight(Collections.singletonList(locationLink));
                                } else {
                                    return Either.forLeft(Collections.singletonList(new Location(locationLink.getTargetUri(), locationLink.getTargetRange())));
                                }

                            }
                            Signature sig = sigInst.getLeft();
                            if (sig != null) {
                                Location definitionLocation = getDefinitionLocation(sig);
                                if (definitionLocation == null) {
                                    return null;
                                }

                                if (getServer().getClientCapabilities().getTextDocument().getDefinition().getLinkSupport() == Boolean.TRUE) {
                                    return Either.forRight(Collections.singletonList(new LocationLink(definitionLocation.getUri(), definitionLocation.getRange(), definitionLocation.getRange(), sigInst.getRight())));
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
            final Optional<SootClass<?>> aClass =
                    getServer().getView().getClass((ClassType) sig);
            if (aClass.isPresent()) {
                SootClass<?> sc =  aClass.get();
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
                SootClass<?> sc =  aClass.get();
                final Optional<? extends SootMethod> methodOpt = sc.getMethod(((MethodSignature) sig));
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
                SootClass<?> sc =  aClass.get();
                final Optional<? extends SootField> field = sc.getField(((FieldSignature) sig).getSubSignature());
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

                                return Either.forLeft(list);
                            } else if (sig instanceof MethodSignature) {
                                final Set<ClassType> classTypes =
                                        typeHierarchy.subtypesOf(((MethodSignature) sig).getDeclClassType());
                                classTypes.forEach(
                                        csig -> {
                                            Optional<SootClass<?>> scOpt =  view.getClass(csig);
                                            if (scOpt.isPresent()) {
                                                final SootClass<?> sc = scOpt.get();
                                                final Optional<? extends SootMethod> methodOpt =
                                                        sc.getMethod(((MethodSignature) sig).getSubSignature());
                                                final String methodsClassUri =
                                                        Util.pathToUri(sc.getClassSource().getSourcePath());
                                                methodOpt.ifPresent(
                                                        method -> list.add(
                                                                Util.positionToDefLocation(
                                                                        methodsClassUri, method.getPosition())));
                                            }
                                        });

                                return Either.forLeft(list);
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
                                final Optional<SootClass<?>> aClass =
                                        view.getClass(classType);
                                if (aClass.isPresent()) {
                                    SootClass<?> sc = aClass.get();
                                    Path path = Util.uriToPath(uri);
                                    ParseTree parseTree = docParseTree.get(path);
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
                        ParseTree parseTree = getParseTree(path);
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

    private ParseTree getParseTree(@Nonnull Path path) {
        return docParseTree.computeIfAbsent(
                path,
                k -> {
                    try {
                        JimpleParser jimpleParser =
                                JimpleConverterUtil.createJimpleParser(CharStreams.fromPath(path), path);
                        return jimpleParser.file();
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

                                final Optional<SootClass<?>> aClass =
                                        getServer().getView().getClass(classType);
                                if (!aClass.isPresent()) {
                                    return null;
                                }
                                SootClass<?> sc = aClass.get();

                                // maybe: cache instance for this file like for sigs
                                Path path = Util.uriToPath(uri);
                                ParseTree parseTree = docParseTree.get(path);
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
                                    final Optional<? extends SootMethod> method = sc.getMethod(((MethodSignature) sig));
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
                                    str = Modifier.toString(sc.getModifiers()) + " " + sc;
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
                                        str = Modifier.toString(sootMethod.getModifiers()) + " " + sootMethod;
                                    }
                                }
                            } else if (sig instanceof FieldSignature) {
                                final Optional<SootClass<?>> aClass =
                                        getServer().getView().getClass(((FieldSignature) sig).getDeclClassType());
                                if (aClass.isPresent()) {
                                    SootClass<?> sc = aClass.get();
                                    final Optional<? extends SootField> aField = sc.getField(((FieldSignature) sig).getSubSignature());
                                    if (aField.isPresent()) {
                                        final SootField sootField = aField.get();
                                        str = Modifier.toString(sootField.getModifiers()) + " " + sootField;
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
                            ParseTree parseTree = docParseTree.get(path);
                            if (parseTree == null) {
                                return null;
                            }

                            final LocalPositionResolver resolver = new LocalPositionResolver(path, parseTree);

                            final ClassType classType = getServer().uriToClasstype(uri);
                            if (classType == null) {
                                return null;
                            }
                            final JimpleView view = getServer().getView();
                            final Optional<SootClass<?>> aClass =
                                    view.getClass(classType);
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
                            final Optional<SootClass<?>> aClass =
                                    getServer().getView().getClass(classType);
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
                            final Optional<SootClass<?>> aClass =
                                    getServer().getView().getClass(classType);
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
                            ParseTree parseTree = docParseTree.get(path);
                            if (parseTree == null) {
                                return null;
                            }
                            return SyntaxHighlightingProvider.paintbrush(parseTree);
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
