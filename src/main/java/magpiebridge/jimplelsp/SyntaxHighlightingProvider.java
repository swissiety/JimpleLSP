package magpiebridge.jimplelsp;

import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.JimpleParser;
import de.upb.swt.soot.jimple.parser.JimpleConverterUtil;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * @author Markus Schmidt
 */
public class SyntaxHighlightingProvider {
  final SemanticTokenManager semanticTokenManager;

  public static SemanticTokensLegend createLegend() {
    SemanticTokenTypeEnum[] tokenTypes = new SemanticTokenTypeEnum[]{SemanticTokenTypeEnum.Keyword, SemanticTokenTypeEnum.Comment, SemanticTokenTypeEnum.Modifier, SemanticTokenTypeEnum.Class, SemanticTokenTypeEnum.Member, SemanticTokenTypeEnum.Type, SemanticTokenTypeEnum.Variable, SemanticTokenTypeEnum.Parameter, SemanticTokenTypeEnum.String, SemanticTokenTypeEnum.Number};
    return new SemanticTokensLegend(Arrays.stream(tokenTypes).map(SemanticTokenTypeEnum::toString).collect(Collectors.toList()), Collections.emptyList());
  }

  public SyntaxHighlightingProvider(@Nonnull SemanticTokensLegend legend) {
    semanticTokenManager = new SemanticTokenManager(legend);
  }

  @Nullable
  public SemanticTokens paintbrush(@Nonnull String uri) {

    final Path path = Util.uriToPath(uri);
    final JimpleParser parser;
    try {
      parser = JimpleConverterUtil.createJimpleParser(CharStreams.fromPath(path), path);
    } catch (IOException exception) {
      exception.printStackTrace();
      return null;
    }

    ParseTreeWalker walker = new ParseTreeWalker();
    SyntaxHighlightingListener listener = new SyntaxHighlightingListener();
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

  private class SyntaxHighlightingListener extends JimpleBaseListener {

    private void paint(SemanticTokenTypeEnum tokentype, Token token) {
      // TODO: add tokenModifier
      // zero offset line/column
      semanticTokenManager.paintText(tokentype, null, token.getLine() - 1, token.getCharPositionInLine(), token.getText().length());
    }

    private void paint(SemanticTokenTypeEnum tokentype, ParserRuleContext ctx) {
      // TODO: add tokenModifier
      // zero offset line/column
      semanticTokenManager.paintText(tokentype, null, ctx.start.getLine() - 1, ctx.start.getCharPositionInLine(), ctx.getText().length());
    }


    @Override
    public void enterModifier(JimpleParser.ModifierContext ctx) {
      // mark modifier
      paint(SemanticTokenTypeEnum.Modifier, ctx);
      super.enterModifier(ctx);
    }

    @Override
    public void enterType(JimpleParser.TypeContext ctx) {
      // mark type as type
      paint(SemanticTokenTypeEnum.Type, ctx);
      super.enterType(ctx);
    }

    @Override
    public void enterIdentifier(JimpleParser.IdentifierContext ctx) {
      // TODO: paint(SemanticTokenTypeEnum.Variable, ctx);
      super.enterIdentifier(ctx);
    }

    @Override
    public void enterExtends_clause(JimpleParser.Extends_clauseContext ctx) {
      // mark extends as keyword
      paint(SemanticTokenTypeEnum.Keyword, ctx.start);
      super.enterExtends_clause(ctx);
    }

    @Override
    public void enterImplements_clause(JimpleParser.Implements_clauseContext ctx) {
      //  mark implements as keyword
      paint(SemanticTokenTypeEnum.Keyword, ctx.start);
      super.enterImplements_clause(ctx);
    }

    @Override
    public void enterImportItem(JimpleParser.ImportItemContext ctx) {
      // mark import as keyword
      paint(SemanticTokenTypeEnum.Keyword, ctx.start);
      super.enterImportItem(ctx);
    }

    @Override
    public void enterStatement(JimpleParser.StatementContext ctx) {
      // mark "label" on stmt as keyword
      paint(SemanticTokenTypeEnum.Keyword, ctx.start);
      super.enterStatement(ctx);
    }

    @Override
    public void enterStmt(JimpleParser.StmtContext ctx) {
      // TODO: keyword: switch, if
      super.enterStmt(ctx);
    }

    @Override
    public void enterAssignments(JimpleParser.AssignmentsContext ctx) {
      //paintTokenTypeEnum.Variable, ctx);
      super.enterAssignments(ctx);
    }

    @Override
    public void enterIdentity_ref(JimpleParser.Identity_refContext ctx) {
      // mark @...
      if (ctx.caught != null) {
        paint(SemanticTokenTypeEnum.Keyword, ctx.caught);
      } else if (ctx.parameter_idx != null) {
        paint(SemanticTokenTypeEnum.Keyword, ctx.parameter_idx);
      } else {
        // this
        paint(SemanticTokenTypeEnum.Keyword, ctx);
      }

      super.enterIdentity_ref(ctx);
    }

    @Override
    public void enterGoto_stmt(JimpleParser.Goto_stmtContext ctx) {
      // mark goto as keyword
      paint(SemanticTokenTypeEnum.Keyword, ctx.GOTO().getSymbol());
      super.enterGoto_stmt(ctx);
    }

    @Override
    public void enterCase_stmt(JimpleParser.Case_stmtContext ctx) {
      // mark "case" as keyword
      paint(SemanticTokenTypeEnum.Keyword, ctx.start);
      super.enterCase_stmt(ctx);
    }


    @Override
    public void enterInvoke_expr(JimpleParser.Invoke_exprContext ctx) {
      // mark *invoke stmts as keyword
      if (ctx.dynamicinvoke != null) {
        paint(SemanticTokenTypeEnum.Keyword, ctx.dynamicinvoke);
      } else if (ctx.nonstaticinvoke != null) {
        paint(SemanticTokenTypeEnum.Keyword, ctx.nonstaticinvoke);
      } else if (ctx.staticinvoke != null) {
        paint(SemanticTokenTypeEnum.Keyword, ctx.staticinvoke);
      }
      super.enterInvoke_expr(ctx);
    }

    @Override
    public void enterInteger_constant(JimpleParser.Integer_constantContext ctx) {
      paint(SemanticTokenTypeEnum.Number, ctx);
      super.enterInteger_constant(ctx);
    }

  }
}
