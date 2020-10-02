package com.github.swissiety.jimplelsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

public class JimpleTextDocumentService implements TextDocumentService {

  private final JimpleLanguageServer jimpleLanguageServer;
  private LanguageClient client;
  private final Map<String, JimpleDocument> docs = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, Integer> documentVersions = new HashMap<>();

  public JimpleTextDocumentService(JimpleLanguageServer jimpleLanguageServer, LanguageClient client) {
    this.jimpleLanguageServer = jimpleLanguageServer;
    this.client = client;
  }

  /*
  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
  	return CompletableFuture.supplyAsync(() -> Either.forRight(new CompletionList(false, Lists.newArrayList("test","jimple").stream()
  			.map(word -> {
  				CompletionItem item = new CompletionItem(word);
  				item.setInsertText(word);
  				return item;
  			}).collect(Collectors.toList()))));
  }

  @Override
  public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
  	return null;
  }

  @Override
  public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
  	return CompletableFuture.supplyAsync(() -> {
  		JimpleDocumentModel doc = docs.get(position.getTextDocument().getUri());
  		Hover res = new Hover();
  		List<Either<String, MarkedString>> content = doc.getResolvedRoutes().stream()
  			.filter(route -> route.line == position.getPosition().getLine())
  			.map(route -> route.name)
  			.map(JimpleMap.INSTANCE.type::get)
  			.map(this::getHoverContent)
  			.collect(Collectors.toList());
  		res.setContents(content);
  		return res;
  	});
  }

  private Either<String, MarkedString> getHoverContent(String text) {
  	return Either.forLeft("<font color='pink'>" + text + "</font>");		// TODO: adapt HTML ;)
  }

  @Override
  public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
  	return null;
  }

  @Override
  public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
  	return CompletableFuture.supplyAsync(() -> {
  		JimpleDocumentModel doc = docs.get(position.getTextDocument().getUri());
  		String variable = doc.getVariable(position.getPosition().getLine(), position.getPosition().getCharacter());
  		if (variable != null) {
  			int variableLine = doc.getDefintionLine(variable);
  			if (variableLine == -1) {
  				return Collections.emptyList();
  			}
  			Location location = new Location(position.getTextDocument().getUri(), new Range(
  				new Position(variableLine, 0),
  				new Position(variableLine, variable.length())
  				));
  			return Collections.singletonList(location);
  		}
  		return null;
  	});
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
  	return CompletableFuture.supplyAsync(() -> {
  		JimpleDocumentModel doc = docs.get(params.getTextDocument().getUri());
  		String variable = doc.getVariable(params.getPosition().getLine(), params.getPosition().getCharacter());
  		if (variable != null) {
  			return doc.getResolvedRoutes().stream()
  				.filter(route -> route.text.contains("${" + variable + "}") || route.text.startsWith(variable + "="))
  				.map(route -> new Location(params.getTextDocument().getUri(), new Range(
  					new Position(route.line, route.text.indexOf(variable)),
  					new Position(route.line, route.text.indexOf(variable) + variable.length())
  				)))
  				.collect(Collectors.toList());
  		}
  		String routeName = doc.getResolvedRoutes().stream()
  				.filter(route -> route.line == params.getPosition().getLine())
  				.collect(Collectors.toList())
  				.get(0)
  				.name;
  		return doc.getResolvedRoutes().stream()
  				.filter(route -> route.name.equals(routeName))
  				.map(route -> new Location(params.getTextDocument().getUri(), new Range(
  						new Position(route.line, 0),
  						new Position(route.line, route.text.length()))))
  				.collect(Collectors.toList());
  	});
  }

  @Override
  public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
  	return null;
  }

  */

  /*
  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
  	return CompletableFuture.supplyAsync(() ->
  			docs.get(params.getTextDocument().getUri()).getSymbols().map( symbol -> {
  			SymbolInformation symbolInfo = new SymbolInformation();
  			// TODO adapt Positions
  				int line = 2;
  			symbolInfo.setLocation(new Location(params.getTextDocument().getUri(), new Range(
  							new Position(line, '0'), new Position(line, '5'))));
  				symbolInfo.setName("mofu var!");
  				symbolInfo.setKind(SymbolKind.Variable);

  			Either<SymbolInformation, DocumentSymbol> res = Either.forLeft(symbolInfo);
  			return res;
  		}).collect(Collectors.toList())
  	);
  }

  */
  /*
  	@Override
  	public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
  		String docUri = params.getTextDocument().getUri();
  		return CompletableFuture.supplyAsync(() ->
  			params.getContext().getDiagnostics().stream()
  			.map(diagnostic -> {
  				List<CodeAction> res = new ArrayList<>();
  				CodeAction removeAction = new CodeAction("Enlever ce troncon");
  				removeAction.setKind(CodeActionKind.QuickFix);
  				Integer docVersion = this.documentVersions.get(docUri);
  				removeAction.setEdit(new WorkspaceEdit(Collections.singletonList(Either.forLeft(
  						new TextDocumentEdit(new VersionedTextDocumentIdentifier(docUri, docVersion),
  								Collections.singletonList(
  										new TextEdit(diagnostic.getRange(), "")))))));
  				removeAction.setDiagnostics(Collections.singletonList(diagnostic));
  				res.add(removeAction);
  				return res;
  			})
  			.flatMap(Collection::stream)
  			.map(codeAction -> {
  				Either<Command, CodeAction> either = Either.forRight(codeAction);
  				return either;
  			}).collect(Collectors.toList())
  		);
  	}

  	@Override
  	public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
  		return CompletableFuture.completedFuture(Collections.emptyList());
  		// TODO IDE feature change
  //		return CompletableFuture.supplyAsync(() -> docs.get(params.getTextDocument().getUri()).getResolvedRoutes().stream().map(route ->
  //			new CodeLens(new Range(new Position(route.line, 0), new Position(route.line, 1)), new Command(jimpleMap.INSTANCE.isLift(route.name) ? "🚡up" : "⛷️down", "kikoo"), null)
  //		).collect(Collectors.toList()));
  	}

  	@Override
  	public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
  		return null;
  	}

  	@Override
  	public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
  		return null;
  	}

  	@Override
  	public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
  		return null;
  	}

  	@Override
  	public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
  		return null;
  	}

  	@Override
  	public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
  		return null;
  	}
  */

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    JimpleDocument model = new JimpleDocument(params.getTextDocument().getText());
    this.docs.put(params.getTextDocument().getUri(), model);
    CompletableFuture.runAsync(
            () ->
                    client
                            .publishDiagnostics(
                                    new PublishDiagnosticsParams(
                                            params.getTextDocument().getUri(), validate(model))));
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    // modify internal state
    this.documentVersions.put(
            params.getTextDocument().getUri(), params.getTextDocument().getVersion() + 1);
    JimpleDocument model = new JimpleDocument(params.getContentChanges().get(0).getText());
    this.docs.put(params.getTextDocument().getUri(), model);
    // send notification
    CompletableFuture.runAsync(
            () -> {
              List<Diagnostic> diagnostics = validate(model);
              client
                      .publishDiagnostics(
                              new PublishDiagnosticsParams(params.getTextDocument().getUri(), diagnostics));
            });
  }

  private List<Diagnostic> validate(JimpleDocument model) {
    List<Diagnostic> res = new ArrayList<>();
    if (false) {
      /*
      Diagnostic diagnostic = new Diagnostic();
      diagnostic.setSeverity(DiagnosticSeverity.Error);
      diagnostic.setMessage("The .jimple file contains an Error");
      diagnostic.setRange(new Range(
      				new Position(line, charOffset),
      				new Position(line, charOffset + length)));

      res.add(diagnostic);
      */
    }
    return res;
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    this.docs.remove(params.getTextDocument().getUri());
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
  }
}
