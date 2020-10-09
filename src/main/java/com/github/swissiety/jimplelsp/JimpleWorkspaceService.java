package com.github.swissiety.jimplelsp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import de.upb.swt.soot.core.jimple.basic.NoPositionInformation;
import de.upb.swt.soot.core.model.SootField;
import de.upb.swt.soot.core.model.SootMethod;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.WorkspaceService;

import javax.annotation.Nonnull;

public class JimpleWorkspaceService implements WorkspaceService {

  @Nonnull
  private final JimpleLanguageServer server;

  public JimpleWorkspaceService(@Nonnull JimpleLanguageServer server) {
    this.server = server;
  }

  @Override
  public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
    List<SymbolInformation> list = new ArrayList<>();
    int limit = 100;

    final String query = params.getQuery().trim().toLowerCase();

    if (query.length() > 1) {
      server.getClasses().forEach(clazz -> {
        if (list.size() >= limit) {
          return;
        }
        findSymbolsInClass(list, query, clazz);
      });
    }
    return CompletableFuture.completedFuture(list);
  }

  private void findSymbolsInClass(List<SymbolInformation> resultList, String query, de.upb.swt.soot.core.model.SootClass clazz) {
    // find classes;
    if (clazz.getName().toLowerCase().startsWith(query)) {
      Location location = new Location(server.classToUri(clazz), server.positionToRange(clazz.getPosition()));
      resultList.add(new SymbolInformation(clazz.getName(), SymbolKind.Class, location));
    }

    // methods
    for (SootMethod method : clazz.getMethods()) {
      if (method.getName().toLowerCase().startsWith(query)) {
        Location location = new Location(server.classToUri(clazz), server.positionToRange(method.getPosition()));
        resultList.add(new SymbolInformation(method.getName(), SymbolKind.Method, location));
      }
    }

    // fields
    for (SootField field : clazz.getFields()) {
      if (field.getName().toLowerCase().startsWith(query)) {
        Location location = new Location(server.classToUri(clazz), server.positionToRange(field.getPosition()));
        resultList.add(new SymbolInformation(field.getName(), SymbolKind.Field, location));
      }
    }
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
  }
}
