package com.github.swissiety.jimplelsp.provider;

import com.github.swissiety.jimplelsp.Util;
import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.JimpleParser;
import de.upb.swt.soot.jimple.parser.JimpleConverterUtil;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

/** @author Markus Schmidt */
public class SyntaxHighlightingProvider {
  static final String[] tokenTypes =
      new String[] {
        SemanticTokenTypes.Keyword,
        SemanticTokenTypes.Comment,
        SemanticTokenTypes.Modifier,
        SemanticTokenTypes.Class,
        SemanticTokenTypes.Member,
        SemanticTokenTypes.Type,
        SemanticTokenTypes.Variable,
        SemanticTokenTypes.Parameter,
        SemanticTokenTypes.String,
        SemanticTokenTypes.Number
      };

  static final SemanticTokenManager semanticTokenManager = new SemanticTokenManager(createLegend());

  public static SemanticTokensLegend createLegend() {

    return new SemanticTokensLegend(
        Arrays.asList(tokenTypes),
        // TODO: add modifiers too
        Collections.emptyList());
  }

  @Nullable
  public static SemanticTokens paintbrush(@Nonnull TextDocumentIdentifier doc) {

    final Path path = Util.uriToPath(doc.getUri());
    final JimpleParser parser;
    try {
      parser = JimpleConverterUtil.createJimpleParser(CharStreams.fromPath(path), path);
    } catch (IOException exception) {
      exception.printStackTrace();
      return null;
    }

    ParseTreeWalker walker = new ParseTreeWalker();
    SyntaxHighlightingListener listener = new SyntaxHighlightingListener(semanticTokenManager);
    walker.walk(listener, parser.file());

    return new SemanticTokens(semanticTokenManager.getCanvas());
  }

  public SemanticTokensLegend getLegend() {
    return semanticTokenManager.getLegend();
  }

  // TODO: [ms] planned supported types:
  //  SemanticTokenTypeEnum.Keyword, SemanticTokenTypeEnum.Comment,
  //  SemanticTokenTypeEnum.Modifier, SemanticTokenTypeEnum.Class, SemanticTokenTypeEnum.Member,
  //  SemanticTokenTypeEnum.Type, SemanticTokenTypeEnum.Variable,   SemanticTokenTypeEnum.Parameter,
  //  SemanticTokenTypeEnum.String,  SemanticTokenTypeEnum.Number

  private static class SyntaxHighlightingListener extends JimpleBaseListener {

    @Nonnull private final SemanticTokenManager semanticTokenManager;

    private SyntaxHighlightingListener(SemanticTokenManager semanticTokenManager) {
      this.semanticTokenManager = semanticTokenManager;
    }

    private void paint(String tokentype, Token token) {
      // TODO: add tokenModifier
      // zero offset line/column
      semanticTokenManager.paintText(
          tokentype,
          null,
          token.getLine() - 1,
          token.getCharPositionInLine(),
          token.getText().length());
    }

    private void paint(String tokentype, ParserRuleContext ctx) {
      // TODO: add tokenModifier
      // zero offset line/column
      semanticTokenManager.paintText(
          tokentype,
          null,
          ctx.start.getLine() - 1,
          ctx.start.getCharPositionInLine(),
          ctx.getText().length());
    }

    @Override
    public void enterIdentifier(JimpleParser.IdentifierContext ctx) {
      paint(SemanticTokenTypes.Variable, ctx);
      super.enterIdentifier(ctx);
    }

    @Override
    public void enterAssignments(JimpleParser.AssignmentsContext ctx) {
      // paintTokenTypes.Variable, ctx);
      super.enterAssignments(ctx);
    }

    @Override
    public void enterExtends_clause(JimpleParser.Extends_clauseContext ctx) {
      // TODO: keyword
      //     paintTokenTypes.Keyword, ctx);
      super.enterExtends_clause(ctx);
    }

    @Override
    public void enterImplements_clause(JimpleParser.Implements_clauseContext ctx) {
      // TODO: keyword
      //     paintTokenTypes.Keyword, ctx);
      super.enterImplements_clause(ctx);
    }

    @Override
    public void enterImportItem(JimpleParser.ImportItemContext ctx) {
      // TODO: keyword
      //     paintTokenTypes.Keyword, ctx);
      super.enterImportItem(ctx);
    }

    @Override
    public void enterInvoke_expr(JimpleParser.Invoke_exprContext ctx) {
      if (ctx.dynamicinvoke != null) {
        paint(SemanticTokenTypes.Keyword, ctx.dynamicinvoke);
      } else if (ctx.nonstaticinvoke != null) {

      } else if (ctx.staticinvoke != null) {

      }

      //     paintTokenTypeEnum.Keyword, ctx);
      super.enterInvoke_expr(ctx);
    }
  }
}
