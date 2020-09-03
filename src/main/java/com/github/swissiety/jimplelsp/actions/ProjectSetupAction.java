package com.github.swissiety.jimplelsp.actions;

import com.github.swissiety.jimplelsp.JimpleLanguageServer;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import soot.Main;

/** Helps the User extracting jimple code to his working directory */
public class ProjectSetupAction {

  /** Search for .jimple files in the workspace */
  void scanWorkspace() {

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
            + ". Shall we extract Jimple from that file?");
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

    // TODO: get android*.jar path from client config
    String androidJar = "";

    if (androidJar.isEmpty()) {
      JimpleLanguageServer.getClient()
          .logMessage(
              new MessageParams(
                  MessageType.Error,
                  "Can not Extract Jimple from the APK.\n"
                      + "Please set the path, where we can find the respective android.jar.\n"
                      + "Config key: jimplelsp.android-jars "));
      return;
    }

    // extract in temporary
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
          androidJar,
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
                        new CreateFile("TODO-file-uri", new CreateFileOptions(false, true))));
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
