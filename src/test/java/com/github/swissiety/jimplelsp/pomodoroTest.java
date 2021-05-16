package com.github.swissiety.jimplelsp;

import static org.junit.Assert.assertEquals;

import de.upb.swt.soot.core.frontend.AbstractClassSource;
import de.upb.swt.soot.core.model.AbstractClass;
import de.upb.swt.soot.core.model.SootClass;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;

public class pomodoroTest {

  final JimpleLspServer jimpleLspServer = new JimpleLspServer();

  @Before
  JimpleLspServer setup() {

    final InitializeParams params = new InitializeParams();
    Path root = Paths.get("src/test/resources/pomodoro/");
    params.setRootUri(Util.pathToUri(root));
    ClientCapabilities clientCaps = new ClientCapabilities();
    TextDocumentClientCapabilities docCaps = new TextDocumentClientCapabilities();
    docCaps.setDeclaration(new DeclarationCapabilities(true));
    docCaps.setDefinition(new DefinitionCapabilities(true));
    docCaps.setReferences(new ReferencesCapabilities(true));
    docCaps.setImplementation(new ImplementationCapabilities(true));
    docCaps.setDocumentSymbol(new DocumentSymbolCapabilities(true));
    docCaps.setHover(new HoverCapabilities(true));

    clientCaps.setTextDocument(docCaps);

    WorkspaceClientCapabilities wCaps = new WorkspaceClientCapabilities();
    wCaps.setSymbol(
        new SymbolCapabilities(
            new SymbolKindCapabilities(
                Arrays.asList(SymbolKind.Class, SymbolKind.Method, SymbolKind.Field))));
    clientCaps.setWorkspace(wCaps);

    params.setCapabilities(clientCaps);

    jimpleLspServer.initialize(params);
    jimpleLspServer.initialized(new InitializedParams());
    final Collection<? extends AbstractClass<? extends AbstractClassSource>> classes =
        (Collection<SootClass>) jimpleLspServer.getView().getClasses();
    assertEquals("Not all Classes are loaded/parsed", 65, classes.size());

    return jimpleLspServer;
  }

  @Test
  public void testReferences() throws ExecutionException, InterruptedException {

    final ReferenceParams params = new ReferenceParams(new ReferenceContext(false));
    params.setTextDocument(new TextDocumentIdentifier("uri"));
    params.setPosition(new Position(0, 0));
    final CompletableFuture<List<? extends Location>> references =
        jimpleLspServer.getTextDocumentService().references(params);
    final List<? extends Location> locations = references.get();
  }

  @Test
  public void testDefinition() throws ExecutionException, InterruptedException {
    final DefinitionParams position =
        new DefinitionParams(new TextDocumentIdentifier("uri"), new Position(0, 0));
    final CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
        definition = jimpleLspServer.getTextDocumentService().definition(position);
    final Either<List<? extends Location>, List<? extends LocationLink>> listListEither =
        definition.get();

    final Range range = new Range(new Position(0, 0), new Position(0, 0));
  }
}
