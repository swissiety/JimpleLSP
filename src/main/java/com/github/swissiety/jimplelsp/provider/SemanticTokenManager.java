package com.github.swissiety.jimplelsp.provider;

import org.eclipse.lsp4j.SemanticTokensLegend;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/** @author Markus Schmidt */
public class SemanticTokenManager {
  @Nonnull private final SemanticTokensLegend legend;

  @Nonnull final List<Integer> encodedSemanticTokens = new ArrayList<>();
  int lastTokenLine, lastTokenColumn = 0;

  public SemanticTokenManager(@Nonnull SemanticTokensLegend legend) {
    this.legend = legend;
  }

  public void paintText(@Nonnull String type, @Nonnull String mod, int line, int col, int length) {
    //    at index 5*i - deltaLine: token line number, relative to the previous token
    encodedSemanticTokens.add(line - lastTokenLine);

    //    at index 5*i+1 - deltaStart: token start character, relative to the previous token
    // (relative to 0 or the previous token’s start if they are on the same line)
    if (line == lastTokenLine) {
      encodedSemanticTokens.add(col - lastTokenColumn);
    } else {
      encodedSemanticTokens.add(col);
    }

    //    at index 5*i+2 - length: the length of the token.
    encodedSemanticTokens.add(length);

    //    at index 5*i+3 - tokenType: will be looked up in SemanticTokensLegend.tokenTypes. We
    // currently ask that tokenType < 65536.
    final int typeIdx = legend.getTokenTypes().indexOf(type); // TODO [ms] performance
    if (typeIdx < 0) {
      throw new RuntimeException(type + " is not supported in semantic token legend!");
    }
    encodedSemanticTokens.add(typeIdx);

    //    at index 5*i+4 - tokenModifiers: each set bit will be looked up in
    // SemanticTokensLegend.tokenModifiers
    encodedSemanticTokens.add(
        Math.max(legend.getTokenModifiers().indexOf(mod), 0)); // TODO performance

    lastTokenLine = line;
    lastTokenColumn = col;
  }

  public List<Integer> getCanvas() {
    return encodedSemanticTokens;
  }

  public SemanticTokensLegend getLegend() {
    return legend;
  }

  @Override
  public String toString() {
    return "SemanticTokenManager{"
        + "legend="
        + legend
        + ", lastTokenLine="
        + lastTokenLine
        + ", lastTokenColumn="
        + lastTokenColumn
        + ", encodedSemanticTokens=\n"
        + humanReadableTokenList()
        + "\n}";
  }

  public String humanReadableTokenList() {
    StringBuilder sb = new StringBuilder();
    ListIterator<Integer> it = encodedSemanticTokens.listIterator();
    while (it.hasNext()) {
      int deltaLine = it.next();
      int deltaCol = it.next();
      int length = it.next();
      int tokenTypeIdx = it.next();
      int tokenModIdx = it.next();

      sb.append("token: ").append(legend.getTokenTypes().get(tokenTypeIdx));
      sb.append("\tmodifier: ").append(tokenModIdx);
      sb.append("\tdeltaLine: ").append(deltaLine);
      sb.append("\tdeltaCol: ").append(deltaCol);
      sb.append("\tlength: ").append(length);
      sb.append('\n');
    }
    return sb.toString();
  }
}
