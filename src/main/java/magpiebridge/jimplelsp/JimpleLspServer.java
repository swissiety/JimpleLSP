package magpiebridge.jimplelsp;

import com.google.gson.JsonObject;
import de.upb.swt.soot.core.frontend.AbstractClassSource;
import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.frontend.SootClassSource;
import de.upb.swt.soot.core.inputlocation.EagerInputLocation;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.views.View;
import de.upb.swt.soot.jimple.parser.JimpleConverter;
import de.upb.swt.soot.jimple.parser.JimpleProject;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.ServerConfiguration;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.eclipse.lsp4j.*;
import soot.Main;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static magpiebridge.jimplelsp.Util.positionToDefRange;

/** @author Markus Schmidt */
public class JimpleLspServer extends MagpieServer {

  @Nonnull private final Map<String, SootClassSource> textDocumentClassMapping = new HashMap<>();
  private View view;
  private boolean isViewDirty = true;

  public JimpleLspServer() {
    super(new ServerConfiguration());
  }

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
            // TODO: send publishDiagnostics!
            e.printStackTrace();
          }
          return null;
        },
        THREAD_POOL);
  }

  @Nonnull
  ClientCapabilities getClientCapabilities() {
    return clientConfig;
  }

  @Nonnull
  public View getView() {
    if (isViewDirty) {
      view = recreateView();
      isViewDirty = false;
    }
    return view;
  }

  @Nonnull
  private View recreateView() {
    Map<ClassType, AbstractClassSource> map = new HashMap<>();
    textDocumentClassMapping.forEach((key, value) -> map.put(value.getClassType(), value));
    final JimpleProject project = new JimpleProject(new EagerInputLocation(map));
    return project.createFullView();
  }

  public boolean quarantineInputOrUpdate(@Nonnull String uri) throws ResolveException, IOException {
    return quarantineInputOrUpdate(uri, CharStreams.fromPath(Util.uriToPath(uri)));
  }

  public boolean quarantineInputOrUpdate(@Nonnull String uri, String content)
      throws ResolveException {
    return quarantineInputOrUpdate(uri, CharStreams.fromString(content));
  }

  private boolean quarantineInputOrUpdate(@Nonnull String uri, @Nonnull CharStream charStream)
      throws ResolveException {
    final JimpleConverter jimpleConverter = new JimpleConverter();
    try {
      SootClassSource scs =
          jimpleConverter.run(charStream, new EagerInputLocation(), Util.uriToPath(uri));
      // input is clean
      final SootClassSource overriden = textDocumentClassMapping.put(uri, scs);
      if (overriden != null) {
        // possible optimization: compare if classes are still equal -> set dirty bit only when
        // necessary
      }
      isViewDirty = true;
      return true;
    } catch (ResolveException e) {
      // feed error into diagnostics
      final Diagnostic d =
          new Diagnostic(
              positionToDefRange(e.getRange()),
              e.getMessage(),
              DiagnosticSeverity.Error,
              "JimpleParser");
      client.publishDiagnostics(new PublishDiagnosticsParams(uri, Collections.singletonList(d)));
      return false;
    } catch (Exception e) {
      // feed error into diagnostics
      final Diagnostic d =
          new Diagnostic(
              new Range(new Position(0, 0), new Position(1, 0)),
              e.getMessage(),
              DiagnosticSeverity.Error,
              "JimpleParser");
      // FIXME: merge with other diagnostics in magpie
      client.publishDiagnostics(new PublishDiagnosticsParams(uri, Collections.singletonList(d)));
      return false;
    }
  }

  @Override
  public void exit() {
    // FIXME: don't die in development
    logger.cleanUp();
    MagpieServer.ExceptionLogger.cleanUp();
  }

  List<WorkspaceFolder> workspaceFolders = Collections.emptyList();

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    if (params.getWorkspaceFolders() != null) {
      workspaceFolders = params.getWorkspaceFolders();
    }

    final CompletableFuture<InitializeResult> initialize = super.initialize(params);
    try {
      final ServerCapabilities capabilities = initialize.get().getCapabilities();
      capabilities.setWorkspaceSymbolProvider(true);
      capabilities.setDocumentSymbolProvider(true);

      capabilities.setImplementationProvider(true);
      capabilities.setDefinitionProvider(true);
      capabilities.setReferencesProvider(true);
      capabilities.setHoverProvider(true);

      capabilities.setTypeDefinitionProvider(true);
      capabilities.setFoldingRangeProvider(false);
      capabilities.setDocumentHighlightProvider(true);
      // check: capabilities.setDocumentFormattingProvider(true);

    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
      return null;
    }

    return initialize;
  }

  @Override
  public void initialized(InitializedParams params) {
    super.initialized(params);

    long startNanos = System.nanoTime();

    pool(() -> {
      List<Path> rootpaths = new ArrayList<>(workspaceFolders.size() + 1);
      rootPath.ifPresent(rootpaths::add);

      workspaceFolders.forEach(
              f -> {
                final Path path = Util.uriToPath(f.getUri()).toAbsolutePath();
                if (rootpaths.stream()
                        .noneMatch(existingPath -> path.startsWith(existingPath.toAbsolutePath()))) {
                  // add workspace folder if its not a subdirectory of an already existing path
                  rootpaths.add(path);
                }
              });

      final boolean jimpleFound = scanWorkspaceForJimple(rootpaths);
      if (!jimpleFound) {
        extractFromAPKJAR(rootpaths);
      }


      double runtimeMs = (System.nanoTime() - startNanos) / 1e6;
      // TODO: channel to info log if necessary: System.out.println("Workspace indexing took " +
      // runtimeMs + " ms");

      return null;
    });
  }

  private void extractFromAPKJAR(List<Path> rootpaths) {
    // find apk in top levels/first level subdir
    if (clientConfig.getWorkspace().getConfiguration() == Boolean.TRUE) {

      List<Path> apkJarFiles = new ArrayList<>();
      // get ANDROIDHOME config from client
      final ConfigurationItem configurationItem = new ConfigurationItem();
      configurationItem.setSection("JimpleLSP.jimpleextraction");
      final CompletableFuture<List<Object>> configuration = client.configuration(new ConfigurationParams(Collections.singletonList(configurationItem)));

      final List<Object> configItems;
      try {
        configItems = configuration.get();
        if (configItems.isEmpty()) {
          return;
        }
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
        return;
      }

      /* TODO: if not overriden in configuration try to get info frome $ANDROID_HOME global variable if set
       final String androidhomeEnv = System.getenv("ANDROID_HOME");
       // exists?
       androidhomeEnv += "/";
       // exists folder?
       */


      final JsonObject o = (JsonObject) configItems.get(0);
      final String androidplatform = o.get("androidplatforms").getAsString();

      for (Path rootpath : rootpaths) {
        // find apk
        try (Stream<Path> paths = Files.walk(rootpath)) {
          paths.filter(f -> {
            final String filename = f.toString().toLowerCase();
            return filename.endsWith(".apk") || filename.endsWith(".jar");
          }).forEach(apkJarFiles::add);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      if (apkJarFiles.size() > 0) {

        final MessageActionItem extract_all = new MessageActionItem("Extract all");
        // single / multiple containers found
        final ShowMessageRequestParams messageRequestParams = new ShowMessageRequestParams();

        StringBuilder sb = new StringBuilder();
        List<MessageActionItem> actions = new ArrayList<>();
        if (apkJarFiles.size() > 1) {
          sb.append("JimpleLSP found multiple APKs/Jars. Do you like to extract Jimple from them?\n");
          actions.add(extract_all);

          for (Path apkJarFile : apkJarFiles) {
            sb.append("- ").append(apkJarFile.toString()).append("\n");
            actions.add(new MessageActionItem(apkJarFile.toString()));
          }

          actions.add(new MessageActionItem("Cancel"));

        } else {
          final Path apkJarFile = apkJarFiles.get(0);
          sb.append("Do you like to extract Jimple from the following file ?");
          actions.add(new MessageActionItem(apkJarFile.toString()));
          actions.add(new MessageActionItem("No"));

        }


        messageRequestParams.setMessage(sb.toString());
        messageRequestParams.setActions(actions);
        messageRequestParams.setType(MessageType.Info);

        final CompletableFuture<MessageActionItem> requestFutur = client.showMessageRequest(messageRequestParams);

        requestFutur.thenApplyAsync((MessageActionItem messageActionItem) -> {
          if (messageActionItem.equals(extract_all)) {
            for (Path apkJarFile : apkJarFiles) {
              extractAPKJAR(apkJarFile, Paths.get(androidplatform));
            }
          } else {
            // FIXME: security! check if answer is from proposed list
            extractAPKJAR(Paths.get(messageActionItem.getTitle()), Paths.get(androidplatform));
          }

          return messageActionItem;
        });

      }

    }
  }

  private void extractAPKJAR(Path target, Path androidPlatforms) {
    try {

      // build output directory name
      final String absoluteFilename = target.toAbsolutePath().toString();
      File outputdir = new File(absoluteFilename.substring(0, absoluteFilename.length() - 4));

      // dont overwrite
      if (outputdir.exists()) {
        client.showMessage(new MessageParams(MessageType.Error, "Output Directory " + outputdir + " exists already."));
        return;
      }
      if (!outputdir.mkdir()) {
        client.showMessage(new MessageParams(MessageType.Error, "Can not create directory  \"" + outputdir + "\" for extracted files."));
        return;
      }

      String[] options;
      if (target.toString().toLowerCase().endsWith("apk")) {

        if (androidPlatforms.toString().isEmpty()) {
          client.showMessage(new MessageParams(MessageType.Error, "The Configuration for androidplatform is empty."));
          return;
        }

        if (!Files.exists(androidPlatforms)) {
          client.showMessage(new MessageParams(MessageType.Error, "Configured androidplatform path \"" + androidPlatforms.toString() + "\" does not exist."));
          return;
        }

        // for APK
        options =
                new String[]{
                        "-process-dir",
                        target.toString(),
                        "-pp",
                        "src-prec",
                        "apk",
                        "android-jars",
                        androidPlatforms.toString(),
                        "-allow-phantom-refs",
                        "-d",
                        outputdir.toString(),
                        "-output-format",
                        "J"
                };

      } else {
        // for JAR
        options = new String[]{
                "-process-dir",
                target.toString(),
                "-pp",
                "src-prec",
                "c",
                "-allow-phantom-refs",
                "-d",
                outputdir.toString(),
                "-output-format",
                "J"
        };


      }
      Main.main(options);


    } catch (RuntimeException e) {
      client.showMessage(new MessageParams(MessageType.Error, e.getMessage()));
      e.printStackTrace();
    }

  }

  private boolean scanWorkspaceForJimple(Iterable<Path> rootpaths) {
    // scan workspace all jimple files <-> classes
    List<Path> jimpleFiles = new ArrayList<>();

    // scan all workspaces in depth for jimple files
    for (Path rootpath : rootpaths) {
      // jimple
      try (Stream<Path> paths = Files.walk(rootpath)) {
        paths.filter(f -> f.toString().endsWith(".jimple")).forEach(jimpleFiles::add);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    for (Path jimpleFile : jimpleFiles) {
      try {
        final String uri = Util.pathToUri(jimpleFile);
        quarantineInputOrUpdate(uri);
      } catch (IOException exception) {
        exception.printStackTrace();
      }
    }

    return !jimpleFiles.isEmpty();
  }

  @Nullable
  public ClassType docIdentifierToClassType(@Nonnull String textDocument) {
    final SootClassSource sootClassSource = textDocumentClassMapping.get(textDocument);
    if (sootClassSource == null) {
      return null;
    }
    return sootClassSource.getClassType();
  }

  @Nullable
  public ClassType uriToClasstype(@Nonnull String strUri) {
    final SootClassSource scs = textDocumentClassMapping.get(strUri);
    if (scs == null) {
      return null;
    }
    return scs.getClassType();
  }
}
