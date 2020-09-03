package com.github.swissiety.jimplelsp.actions;

import com.github.swissiety.jimplelsp.JimpleLanguageServer;
import com.google.common.collect.Lists;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import soot.Main;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 *
 * Helps the User extracting jimple code to his working directory
 *
 * */

public class ProjectSetupAction {

  /**
   *  Search for .jimple files in the workspace
   * */
  void scanWorkspace(){

    boolean jimpleExists = false;
    String foundArchive = null;

    if( !jimpleExists && foundArchive != null ){
      askUser( foundArchive );
    }

  }

  void askUser(String foundArchive){

    final ShowMessageRequestParams requestParams = new ShowMessageRequestParams();
    requestParams.setType( MessageType.Info );
    requestParams.setMessage("JimpleLanguage Server has no \".jimple\" files detected in your workspace. But we found "+ foundArchive +". Shall we extract Jimple from that file?");
    requestParams.setActions(Lists.newArrayList(new MessageActionItem("Extract Jimple"), new MessageActionItem("No")/*, new MessageActionItem("Don't ask again.") */));
    final CompletableFuture<MessageActionItem> messageActionItemCompletableFuture = JimpleLanguageServer.getClient().showMessageRequest(requestParams);

    try {
      final MessageActionItem messageActionItem = messageActionItemCompletableFuture.get();
      if( messageActionItem.getTitle().startsWith("Extract")){
        extractJimple();
      }
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }


  void extractJimple(){

    // TODO: generate with old soot
    String filepath = "tralalala.apk";
    String rtJar = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";

    //String[] options = new String[]{"-cp", classpath, "-f", "jimple", "TryWithResources"};
    String[] options = new String[]{"-cp", filepath + File.pathSeparator + rtJar, "-f", "jimple", "Multilocals"};
    Main.main(options);




    // TODO: create files under workspace via lsp
    final ApplyWorkspaceEditParams params = new ApplyWorkspaceEditParams();
    final WorkspaceEdit edit = new WorkspaceEdit();
    final List<Either<TextDocumentEdit, ResourceOperation>> changeList = new ArrayList<>();

    {
      changeList.add(Either.forRight(new CreateFile("TODO-file-uri", new CreateFileOptions(false, true))));
    }
    edit.setDocumentChanges( changeList );
    params.setEdit(edit);
    params.setLabel("extract Source Archive.");
    JimpleLanguageServer.getClient().applyEdit(params);

  }

}
