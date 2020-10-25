package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.frontend.SootClassSource;
import de.upb.swt.soot.core.inputlocation.AnalysisInputLocation;
import de.upb.swt.soot.core.inputlocation.EagerInputLocation;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.views.View;
import de.upb.swt.soot.jimple.parser.JimpleConverter;
import de.upb.swt.soot.jimple.parser.JimpleProject;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.ServerConfiguration;
import org.antlr.v4.runtime.CharStreams;
import org.eclipse.lsp4j.*;

// FIXME: sth with sourcefilemanager
public class JimpleLspServer extends MagpieServer {

  @Nonnull final AnalysisInputLocation dummyInputLocation = new EagerInputLocation();
  private boolean validCache = false;

  private View view;
  @Nonnull private List<AnalysisInputLocation> analysisInputLocations = new ArrayList<>();
  private Map<TextDocumentIdentifier, SootClassSource> docClassMapping = new HashMap<>();

  @Nonnull List<Diagnostic> diagnostics = new ArrayList<>();

  public JimpleLspServer(ServerConfiguration config) {
    super(config);
    this.textDocumentService = new JimpleTextDocumentService(this);
    this.workspaceService = new JimpleWorkspaceService(this);
  }

  @Nonnull
  public <T> CompletableFuture<T> pool(Callable<T> lambda) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return lambda.call();
          } catch (Throwable e) {
            e.printStackTrace();
            return null;
          }
        },
        THREAD_POOL);
  }

  @Nonnull
  ClientCapabilities getClientCapabilities() {
    return clientConfig;
  }

  @Nonnull
  public View getView() {
    if (!validCache) {
      recreateView();
    }
    return view;
  }

  @Nonnull
  View recreateView() {
    analysisInputLocations.add(new EagerInputLocation());
    final JimpleProject project = new JimpleProject(analysisInputLocations);
    return project.createOnDemandView();
  }

  @Nullable
  SootClassSource quarantineInput(String uri, String content) throws ResolveException {
    final JimpleConverter jimpleConverter = new JimpleConverter();
    try {
      SootClassSource scs =
          jimpleConverter.run(
              CharStreams.fromString(content, uri), dummyInputLocation, Paths.get(uri));
      // input is clean
      return scs;
    } catch (ResolveException e) {
      // feed error into diagnostics
      // FIXME uncomment addDiagnostic(uri, new Diagnostic(positionToRange(e.getRange()),
      // e.getMessage(), DiagnosticSeverity.Error, "JimpleParser"));
      return null;
    }
  }

  List<WorkspaceFolder> workspaceFolders = Collections.emptyList();

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    workspaceFolders = params.getWorkspaceFolders();

    final CompletableFuture<InitializeResult> initialize = super.initialize(params);
    try {
      final ServerCapabilities capabilities = initialize.get().getCapabilities();
      capabilities.setWorkspaceSymbolProvider(true);
      // TODO: capabilities.setDocumentSymbolProvider(true);
      // TODO: capabilities.setDefinitionProvider(true);
      // TODO: capabilities.setTypeDefinitionProvider(true);
      // TODO: capabilities.setImplementationProvider(true);
      // TODO: capabilities.setReferencesProvider(true);

    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
      return null;
    }

    return initialize;
  }

  @Override
  public void initialized(InitializedParams params) {
    super.initialized(params);

    if (rootPath.isPresent()) {
      // TODO scan workspace jimple files <-> classes
    }
    // TODO:    workspaceFolders.forEach();

    // nice2have: implement asking to extract jimple from an apk with old soot
  }

  public ClassType docIdentifierToClassType(TextDocumentIdentifier textDocument) {
    return docClassMapping.get(textDocument).getClassType();
  }
}
