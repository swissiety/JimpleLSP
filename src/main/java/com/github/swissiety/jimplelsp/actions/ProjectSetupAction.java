package com.github.swissiety.jimplelsp.actions;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import soot.Main;

/**
 * Helps the User extracting jimple code to his working directory
 */
public class ProjectSetupAction {

  /** Search for .jimple files in the workspace
   * @param workspaceFolders
   * @param workspaceEditSupport*/
  public static void scanWorkspace(LanguageClient client, Iterable<? extends WorkspaceFolder> workspaceFolders, boolean workspaceEditSupport) {

    List<Path> jimpleFiles = new ArrayList<>();
    List<Path> apkFiles = new ArrayList<>();

    for (WorkspaceFolder workspaceFolder : workspaceFolders) {

      // jimple
      try (Stream<Path> paths = Files.walk(Paths.get(workspaceFolder.getUri()))) {
        paths.filter(f -> f.toString().endsWith(".jimple")).forEach(jimpleFiles::add);
      } catch (IOException e) {
        e.printStackTrace();
      }

      // apk
      try (Stream<Path> paths = Files.walk(Paths.get(workspaceFolder.getUri()), 2)) {
        paths.filter(f -> f.toString().endsWith(".apk")).limit(2).forEach(apkFiles::add);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    /*
    if (jimpleFiles.isEmpty() && apkFiles.size() == 1) {
      final String foundArchive = apkFiles.get(0).toString();
      if (askUser(client, foundArchive)) {
        boolean ret = extractJimple(foundArchive);
        if( ! ret ){
          client.logMessage(
                  new MessageParams(
                          MessageType.Error,
                          "Can not extract Jimple from the APK. The android.jar can not be downloaded. \n"
                          //  + "Please set the path, where we can find the respective android.jar.\n"
                          //  + "Config key: jimplelsp.android-jars "
                  ));
        }
      }
    }
    */
  }

  static boolean askUser(LanguageClient client, String foundArchive) {

    final ShowMessageRequestParams requestParams = new ShowMessageRequestParams();
    requestParams.setType(MessageType.Info);
    requestParams.setMessage(
            "We found no \".jimple\" files detected in your workspace.\n" +
                    "But we found \"" + foundArchive + "\".\n" +
                    "Do you want to extract Jimple from that file?");
    requestParams.setActions(
            Lists.newArrayList(
                    new MessageActionItem("Extract Jimple"),
                    new MessageActionItem("No")
                    /*, new MessageActionItem("Don't ask again.") */));
    final CompletableFuture<MessageActionItem> messageActionItemCompletableFuture =
            client.showMessageRequest(requestParams);

    try {
      final MessageActionItem messageActionItem = messageActionItemCompletableFuture.get();
      return messageActionItem.getTitle().startsWith("Extract");

    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
      return false;
    }
  }

  static boolean extractJimple(String archivePath) {

    // TODO: ask before downloading respective jar if not existing
    // TODO: get existing androidjar from config
    int apkVersion = ApkAndAndroidjar.extractApkVersion(archivePath);
    String lspServerDir = System.getProperty("user.dir");
    final String androidJarPath = lspServerDir + "/android/" + apkVersion + "/android.jar";
    if (!Files.exists(Paths.get(androidJarPath))) {
      final boolean res = ApkAndAndroidjar.downloadAndroidjar(apkVersion, androidJarPath);
      if (!res) {
        return false;
      }
    }

    // extract jimple files to temporary dir
    final Path extractionDir;
    try {
      extractionDir = Files.createTempDirectory("LspServerExtract");
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }

    // generate with old soot
    String rtJar =
            System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";

    // TODO: adapt Soots cli params
    String[] options =
            new String[]{
                    "-cp",
                    archivePath + File.pathSeparator + rtJar,
                    "-android-jars",
                    androidJarPath,
                    "-hierarchy-dirs",
                    "-f",
                    "jimple",
                    archivePath
            };
    Main.main(options);

    /*
    // create files under workspace via lsp
    final ApplyWorkspaceEditParams params = new ApplyWorkspaceEditParams();
    final WorkspaceEdit edit = new WorkspaceEdit();
    final List<Either<TextDocumentEdit, ResourceOperation>> changeList = new ArrayList<>();

    try {
      Files.walk(extractionDir)
              .forEach(
                      file -> {
                        // FIXME: choose workspacefolder: use the first if multiple are given
                        changeList.add(
                                Either.forRight(
                                        new CreateFile(
                                                "./jimple/" + file.relativize(extractionDir),
                                                new CreateFileOptions(false, true))));
                      });
    } catch (IOException e) {
      e.printStackTrace();
    }

    edit.setDocumentChanges(changeList);
    params.setEdit(edit);
    params.setLabel("extract Source Archive.");
    client.applyEdit(params);

     */

    // TODO: cleanup nonempty tempdir?
    return true;
  }
}
