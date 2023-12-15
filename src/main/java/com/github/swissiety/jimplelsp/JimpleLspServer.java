package com.github.swissiety.jimplelsp;

import com.github.swissiety.jimplelsp.provider.SyntaxHighlightingProvider;
import com.google.gson.JsonObject;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import sootup.core.cache.provider.MutableFullCacheProvider;
import sootup.core.frontend.ResolveException;
import sootup.core.frontend.SootClassSource;
import sootup.core.inputlocation.EagerInputLocation;
import sootup.core.model.SootClass;
import sootup.core.model.SourceType;
import sootup.core.types.ClassType;
import sootup.jimple.parser.JimpleConverter;
import sootup.jimple.parser.JimpleView;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** @author Markus Schmidt */
public class JimpleLspServer implements LanguageServer {

  private final JimpleTextDocumentService textDocumentService;
  private final WorkspaceService workspaceService;
  LanguageClient client = null;
  private ClientCapabilities clientCapabilities;

  @Nonnull
  private final Map<Path, SootClassSource<? extends SootClass<?>>> textDocumentClassMapping =
      new HashMap<>();

  private JimpleView view;
  private boolean isViewDirty = true;

  // config values
  private String sootpath = "";
  private String androidplatform = "";

  public JimpleLspServer() {
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
            client.logMessage(new MessageParams(MessageType.Error, getStringFrom(e)));
          }
          return null;
        });
  }

  static String getStringFrom(Throwable e) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    final PrintStream printStream = new PrintStream(bos);
    e.printStackTrace(printStream);
    printStream.flush();
    return bos.toString();
  }

  @Nonnull
  ClientCapabilities getClientCapabilities() {
    return clientCapabilities;
  }

  @Nonnull
  public JimpleView getView() {
    if (isViewDirty) {
      view = recreateView();
      isViewDirty = false;
    }
    return view;
  }

  @Nonnull
  private JimpleView recreateView() {
    HashMap<ClassType, SootClassSource> map = new HashMap<>();
    textDocumentClassMapping.forEach((key, value) -> map.put(value.getClassType(), value));
    JimpleView jimpleView = new JimpleView(Collections.singletonList(new EagerInputLocation()), new MutableFullCacheProvider<>());

    // FIXME: set list of modified jimple files

    return jimpleView;
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

      final Path path = Util.uriToPath(uri);
      if (!Files.exists(path)) {
        return false;
      }
      SootClassSource<? extends SootClass<?>> scs =
          jimpleConverter.run(charStream, new EagerInputLocation<>(), path);
      // input is clean
      final SootClassSource<? extends SootClass<?>> overriden =
          textDocumentClassMapping.put(path, scs);
      if (overriden != null) {
        // possible optimization: compare if classes are still equal -> set dirty bit only when
        // necessary
      }
      isViewDirty = true;

      // clean up errors in IDE if the file is valid (again)
      // FIXME: merge with other diagnostics in magpie
      client.publishDiagnostics(new PublishDiagnosticsParams(uri, Collections.emptyList()));

      return true;
    } catch (ResolveException e) {
      // feed error into diagnostics
      final Diagnostic d =
          new Diagnostic(
              Util.positionToDefRange(e.getRange()),
              e.getMessage(),
              DiagnosticSeverity.Error,
              "JimpleParser");
      client.publishDiagnostics(new PublishDiagnosticsParams(uri, Collections.singletonList(d)));
      return false;
    } catch (Exception e) {
      // feed error into diagnostics
      String stackStraceString = getStringFrom(e);

      final Diagnostic d =
          new Diagnostic(
              new Range(new Position(0, 0), new Position(0, Integer.MAX_VALUE)),
              stackStraceString,
              DiagnosticSeverity.Error,
              "JimpleParser");
      // FIXME: merge with other diagnostics in magpie
      client.publishDiagnostics(new PublishDiagnosticsParams(uri, Collections.singletonList(d)));
      return false;
    }
  }

  @Override
  public void exit() {

  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return null;
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return null;
  }

  List<WorkspaceFolder> workspaceFolders = Collections.emptyList();

  private boolean jimpleFound = false;

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    if (params.getWorkspaceFolders() != null) {
      workspaceFolders = params.getWorkspaceFolders();
    }

    final InitializeResult initialize = new InitializeResult();

      final ServerCapabilities capabilities = initialize.getCapabilities();
      capabilities.setWorkspaceSymbolProvider(true);
      capabilities.setDocumentSymbolProvider(true);

      capabilities.setImplementationProvider(true);
      capabilities.setDefinitionProvider(true);
      capabilities.setReferencesProvider(true);
      capabilities.setHoverProvider(true);

      capabilities.setTypeDefinitionProvider(true);
      capabilities.setFoldingRangeProvider(false);
      capabilities.setDocumentHighlightProvider(true);

      // we could announce it even if the client does not support it..
      if (params.getCapabilities().getTextDocument() != null) {
        // semantic token config
        if (params.getCapabilities().getTextDocument().getSemanticTokens() != null) {
          capabilities.setSemanticTokensProvider(
              new SemanticTokensWithRegistrationOptions(
                  SyntaxHighlightingProvider.getLegend(), true));
        }
      }
      // TODO: check capabilities.setDocumentFormattingProvider(true);

      long startNanos = System.nanoTime();
    pool(
        () -> {
          List<Path> rootpaths = new ArrayList<>(workspaceFolders.size() + 1);

          workspaceFolders.forEach(
              f -> {
                final Path path = Util.uriToPath(f.getUri()).toAbsolutePath();
                if (rootpaths.stream()
                    .noneMatch(existingPath -> path.startsWith(existingPath.toAbsolutePath()))) {
                  // add workspace folder if its not a subdirectory of an already existing path
                  rootpaths.add(path);
                }
              });

          jimpleFound = scanDirectoryForJimple(rootpaths);

          double runtimeMs = (System.nanoTime() - startNanos) / 1e6;
          client.logMessage(
              new MessageParams(MessageType.Log, "Workspace indexing took " + runtimeMs + " ms"));
          return null;
        });

    return CompletableFuture.completedFuture(initialize);
  }

  @Override
  public void initialized(InitializedParams params) {

    if (!jimpleFound) {
      extractFromAPKJAR(
          workspaceFolders.stream()
              .map(w -> Util.uriToPath(w.getUri()))
              .collect(Collectors.toList()));
    } else {
      indexJimple(textDocumentClassMapping.keySet());
    }
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    return null;
  }

  private void extractFromAPKJAR(List<Path> rootpaths) {
    // find apk in top levels/first level subdir
    if (clientCapabilities.getWorkspace().getConfiguration() == Boolean.TRUE) {

      List<Path> apkJarFiles = new ArrayList<>();
      // get ANDROIDHOME config from client
      final ConfigurationItem configurationItem = new ConfigurationItem();
      configurationItem.setSection("JimpleLSP.jimpleextraction");
      final CompletableFuture<List<Object>> configuration =
          client.configuration(
              new ConfigurationParams(Collections.singletonList(configurationItem)));

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
      androidplatform = o.get("androidplatforms").getAsString();
      sootpath = o.get("sootpath").getAsString();

      for (Path rootpath : rootpaths) {
        // find apk
        try (Stream<Path> paths = Files.walk(rootpath)) {
          paths
              .filter(
                  f -> {
                    final String filename = f.toString().toLowerCase();
                    return filename.endsWith(".apk") || filename.endsWith(".jar");
                  })
              .forEach(apkJarFiles::add);
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
          sb.append(
              "JimpleLSP found multiple APKs/Jars. Do you like to extract Jimple from them?\n");
          actions.add(extract_all);

          for (Path apkJarFile : apkJarFiles) {
            sb.append("- ").append(apkJarFile.toString()).append("\n");
            actions.add(new MessageActionItem(apkJarFile.toString()));
          }

          actions.add(new MessageActionItem("Cancel"));

        } else {
          final Path apkJarFile = apkJarFiles.get(0);
          sb.append("Do you wish to extract Jimple from the following file?");
          actions.add(new MessageActionItem(apkJarFile.toString()));
          actions.add(new MessageActionItem("No"));
        }

        messageRequestParams.setMessage(sb.toString());
        messageRequestParams.setActions(actions);
        messageRequestParams.setType(MessageType.Info);

        final CompletableFuture<MessageActionItem> requestFutur =
            client.showMessageRequest(messageRequestParams);

        requestFutur.thenApplyAsync(
            (MessageActionItem messageActionItem) -> {
              if (messageActionItem.equals(extract_all)) {
                for (Path apkJarFile : apkJarFiles) {

                  final String absoluteFilename = apkJarFile.toAbsolutePath().toString();
                  File outputdir =
                      new File(absoluteFilename.substring(0, absoluteFilename.length() - 4));

                  if (!extractAPKJAR(apkJarFile, outputdir)) {
                    client.showMessage(
                        new MessageParams(MessageType.Info, "Extraction of Jimple aborted."));
                    break;
                  }
                  scanDirectoryForJimple(Collections.singletonList(outputdir.toPath()));
                }
                client.showMessage(
                    new MessageParams(
                        MessageType.Warning,
                        "You extracted multiple .apk or .jar files into the same workspace. If there are identical fully qualified classnames across these files and hence in the workspace JimpleLSP will have problems resolving the intended class."));
              } else {
                // FIXME: security! check if answer is from proposed list
                Path target = Paths.get(messageActionItem.getTitle());

                final String absoluteFilename = target.toAbsolutePath().toString();
                File outputdir =
                    new File(absoluteFilename.substring(0, absoluteFilename.length() - 4));
                extractAPKJAR(target, outputdir);

                scanDirectoryForJimple(Collections.singletonList(outputdir.toPath()));
              }

              return messageActionItem;
            });
      }
    }
  }

  private boolean extractAPKJAR(Path target, File outputdir) {
    try {

      if (!Files.exists(Paths.get(sootpath))) {
        client.showMessage(
            new MessageParams(
                MessageType.Error,
                "Configured path to the soot executable  \"" + sootpath + "\" does not exist."));
        return false;
      }

      // dont overwrite
      if (outputdir.exists()) {
        client.showMessage(
            new MessageParams(
                MessageType.Error, "Output Directory " + outputdir + " exists already."));
        return false;
      }
      if (!outputdir.mkdir()) {
        client.showMessage(
            new MessageParams(
                MessageType.Error,
                "Can not create directory  \"" + outputdir + "\" for extracted files."));
        return false;
      }

      String[] options;
      if (target.toString().toLowerCase().endsWith("apk")) {

        if (androidplatform.isEmpty()) {
          client.showMessage(
              new MessageParams(
                  MessageType.Error, "The Configuration for androidplatform is empty."));
          return false;
        }

        if (!Files.exists(Paths.get(androidplatform))) {
          client.showMessage(
              new MessageParams(
                  MessageType.Error,
                  "Configured androidplatform path \"" + androidplatform + "\" does not exist."));
          return false;
        }

        // soots arguments for an APK
        options =
            new String[] {
              "-process-dir",
              target.toString(),
              "-pp",
              "-src-prec",
              "apk",
              "-android-jars",
              androidplatform,
              "-allow-phantom-refs",
              "-d",
              outputdir.toString(),
              "-output-format",
              "J"
            };

      } else {
        // soots arguments for a JAR
        options =
            new String[] {
              "-process-dir",
              target.toString(),
              "-pp",
              "-src-prec",
              "c",
              "-allow-phantom-refs",
              "-d",
              outputdir.toString(),
              "-output-format",
              "J"
            };
      }

      // Main.v().run(options);

      Runtime rt = Runtime.getRuntime();
      Process pr =
          rt.exec(
              "java -jar " + sootpath + " soot.Main " + String.join(" ", Arrays.asList(options)));

      final int ret = pr.waitFor();
      if (ret == 0) {
        client.showMessage(
            new MessageParams(MessageType.Info, "Jimple extracted to \"" + outputdir + "\"."));
      } else {
        client.showMessage(new MessageParams(MessageType.Error, pr.getErrorStream().toString()));
      }

    } catch (Exception e) {

      client.showMessage(new MessageParams(MessageType.Error, getStringFrom(e)));
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private boolean scanDirectoryForJimple(Iterable<Path> rootpaths) {
    // scan workspace all jimple files <-> classes
    List<Path> jimpleFiles = new ArrayList<>();

    // scan all workspaces in depth for jimple files
    for (Path rootpath : rootpaths) {
      // jimple
      try (Stream<Path> paths = Files.walk(rootpath)) {
        paths.filter(f -> f.toString().toLowerCase().endsWith(".jimple")).forEach(jimpleFiles::add);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    for (Path jimpleFile : jimpleFiles) {
      textDocumentClassMapping.put(jimpleFile, null);
    }

    return !jimpleFiles.isEmpty();
  }

  private void indexJimple(Collection<Path> jimpleFiles) {
    for (Path jimpleFile : jimpleFiles) {
      try {
        final String uri = Util.pathToUri(jimpleFile);
        quarantineInputOrUpdate(uri);
      } catch (IOException exception) {
        exception.printStackTrace();
      }
    }
  }

  @Nullable
  public ClassType uriToClasstype(@Nonnull String strUri) {
    final SootClassSource<?> sootClassSource = textDocumentClassMapping.get(Util.uriToPath(strUri));
    if (sootClassSource == null) {
      return null;
    }
    return sootClassSource.getClassType();
  }

  public void connectClient(LanguageClient remoteProxy) {
    client = remoteProxy;
  }

  public LanguageClient getClient() {
    return client;
  }
}
