package magpiebridge.jimplelsp;


import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokensLegend;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class SemanticTokenManager {
  @Nonnull
  private final SemanticTokensLegend legend;

  int lastTokenLine, lastTokenColumn = 0;
  final List<Integer> encodedSemanticTokens = new ArrayList<>();

  public SemanticTokenManager(SemanticTokensLegend legend) {
    this.legend = legend;
  }

  public void add(SemanticTokenTypeEnum type, SemanticTokenModifiers mod, int line, int col, int length) {
    //    at index 5*i - deltaLine: token line number, relative to the previous token
    encodedSemanticTokens.add(line - lastTokenLine);

    //    at index 5*i+1 - deltaStart: token start character, relative to the previous token (relative to 0 or the previous token’s start if they are on the same line)
    if (line == lastTokenLine) {
      encodedSemanticTokens.add(col - lastTokenColumn);
    } else {
      encodedSemanticTokens.add(col);
    }

    //    at index 5*i+2 - length: the length of the token.
    encodedSemanticTokens.add(length);

    //    at index 5*i+3 - tokenType: will be looked up in SemanticTokensLegend.tokenTypes. We currently ask that tokenType < 65536.
    final int typeIdx = legend.getTokenTypes().indexOf(type.toString());// TODO [ms] performance -> datastructure
    encodedSemanticTokens.add(Math.max(typeIdx, 0));

    //    at index 5*i+4 - tokenModifiers: each set bit will be looked up in SemanticTokensLegend.tokenModifiers
    encodedSemanticTokens.add(0); // TODO modifiers

    lastTokenLine = line;
    lastTokenColumn = col;
  }

  public List<Integer> getCanvas() {
    return encodedSemanticTokens;
  }

  public SemanticTokensLegend getLegend() {
    return legend;
  }

}

