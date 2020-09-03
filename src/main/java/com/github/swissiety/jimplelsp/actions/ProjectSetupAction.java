package com.github.swissiety.jimplelsp.actions;

import com.github.swissiety.jimplelsp.JimpleLanguageServer;
import com.google.common.collect.Lists;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import soot.Main;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/** Helps the User extracting jimple code to his working directory */
public class ProjectSetupAction {

  /** Search for .jimple files in the workspace */
  void scanWorkspace() {

    final CompletableFuture<List<WorkspaceFolder>> listCompletableFuture = JimpleLanguageServer.getClient().workspaceFolders();



    boolean jimpleExists = false;
    String foundArchive = null;

    if (!jimpleExists && foundArchive != null) {
      askUser(foundArchive);
    }
  }


  void askUser(String foundArchive) {

    final ShowMessageRequestParams requestParams = new ShowMessageRequestParams();
    requestParams.setType(MessageType.Info);
    requestParams.setMessage(
        "JimpleLanguage Server has no \".jimple\" files detected in your workspace. But we found "
            + foundArchive
            + ". Do you want to extract Jimple from that file?");
    requestParams.setActions(
        Lists.newArrayList(
            new MessageActionItem("Extract Jimple"),
            new MessageActionItem("No") /*, new MessageActionItem("Don't ask again.") */));
    final CompletableFuture<MessageActionItem> messageActionItemCompletableFuture =
        JimpleLanguageServer.getClient().showMessageRequest(requestParams);

    try {
      final MessageActionItem messageActionItem = messageActionItemCompletableFuture.get();
      if (messageActionItem.getTitle().startsWith("Extract")) {
        extractJimple(foundArchive);
      }
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }

  void extractJimple(String archivePath) {

    // TODO: ask before downloading respective jar if not existing
    // TODO: get existing androidjar from config
    int apkVersion = ApkAndAndroidjar.extractApkVersion(archivePath);
    String lspServerDir = System.getProperty("user.dir");
    final String androidJarPath = lspServerDir + "/android/" + apkVersion + "/android.jar";
    if( !Files.exists(Paths.get(androidJarPath) )){
      final boolean res = ApkAndAndroidjar.downloadAndroidjar(apkVersion, androidJarPath);
      if( !res ){
        JimpleLanguageServer.getClient()
                .logMessage(
                        new MessageParams(
                                MessageType.Error,
                                "Can not extract Jimple from the APK. android.jar can not be downloaded. \n"
                                //  + "Please set the path, where we can find the respective android.jar.\n"
                                //  + "Config key: jimplelsp.android-jars "
                        ));
        return;
      }
    }

    // extract jimple files to temporary dir
    final Path extractionDir;
    try {
      extractionDir = Files.createTempDirectory("LspServerExtract");
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    // generate with old soot
    String rtJar =
            System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";

    // TODO: adapt Soots cli params
    String[] options =
            new String[] {
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
                                        new CreateFile("./jimple/" + file.relativize(extractionDir), new CreateFileOptions(false, true))));
                      });
    } catch (IOException e) {
      e.printStackTrace();
    }

    edit.setDocumentChanges(changeList);
    params.setEdit(edit);
    params.setLabel("extract Source Archive.");
    JimpleLanguageServer.getClient().applyEdit(params);

    // TODO: cleanup nonempty tempdir?

  }


}
