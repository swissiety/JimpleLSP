package com.github.swissiety.jimplelsp.languagefeatures;

import com.github.swissiety.jimplelsp.JimpleLanguageServer;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.SemanticHighlightingInformation;
import org.eclipse.lsp4j.SemanticHighlightingParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

public class JimpleSemanticHighlighting {

  void build(VersionedTextDocumentIdentifier doc) {

    List<SemanticHighlightingInformation> info = new ArrayList<>();

    // TODO: iterate over parsed jimple
    //    info.add( new SemanticHighlightingInformation(line ,tokens ));

    // TODO: semantictoken request
  }
}
