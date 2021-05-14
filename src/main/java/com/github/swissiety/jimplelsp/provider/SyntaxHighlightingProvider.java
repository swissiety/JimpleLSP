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

  @Nonnull
  static final SemanticTokensLegend legend = new SemanticTokensLegend(   Arrays.asList(SemanticTokenTypes.Keyword,
          SemanticTokenTypes.Comment,
          SemanticTokenTypes.Modifier,
          SemanticTokenTypes.Class,
          SemanticTokenTypes.Function,
          SemanticTokenTypes.Type,
          SemanticTokenTypes.Variable,
          SemanticTokenTypes.Parameter,
          SemanticTokenTypes.String,
          SemanticTokenTypes.Number), Collections.emptyList());

  public static SemanticTokensLegend createLegend() {
    return legend;
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

    SemanticTokenManager semanticTokenManager = new SemanticTokenManager(legend);

    ParseTreeWalker walker = new ParseTreeWalker();
    SyntaxHighlightingListener listener = new SyntaxHighlightingListener(semanticTokenManager);
    walker.walk(listener, parser.file());

    return new SemanticTokens(semanticTokenManager.getCanvas());
  }

  public SemanticTokensLegend getLegend() {
    return createLegend();
  }

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
    public void enterInteger_constant(JimpleParser.Integer_constantContext ctx) {
      paint(SemanticTokenTypes.Number, ctx);
      super.enterInteger_constant(ctx);
    }

    @Override
    public void enterCase_label(JimpleParser.Case_labelContext ctx) {
      paint( SemanticTokenTypes.Keyword, ctx.start );
      super.enterCase_label(ctx);
    }

    @Override
    public void enterModifier(JimpleParser.ModifierContext ctx) {
      paint(SemanticTokenTypes.Modifier, ctx);
      super.enterModifier(ctx);
    }

    @Override
    public void enterMethod_signature(JimpleParser.Method_signatureContext ctx) {
      paint(SemanticTokenTypes.Function, ctx.class_name);
      super.enterMethod_signature(ctx);
    }

    @Override
    public void enterIdentifier(JimpleParser.IdentifierContext ctx) {
      paint(SemanticTokenTypes.Variable, ctx.start);
      super.enterIdentifier(ctx);
    }

    @Override
    public void enterStmt(JimpleParser.StmtContext ctx) {
      if( ctx.IF() != null || ctx.RETURN() != null || ctx.ENTERMONITOR() != null || ctx.ENTERMONITOR() != null || ctx.BREAKPOINT() != null || ctx.SWITCH() != null || ctx.THROW()!= null || ctx.NOP() != null){
        paint(SemanticTokenTypes.Keyword, ctx.start);
      }
      super.enterStmt(ctx);
    }

    @Override
    public void enterType(JimpleParser.TypeContext ctx) {
      paint(SemanticTokenTypes.Type , ctx.start);
      super.enterType(ctx);
    }


    @Override
    public void enterAssignments(JimpleParser.AssignmentsContext ctx) {
      JimpleParser.Identity_refContext identity_refContext = ctx.identity_ref();
      if( identity_refContext != null){
          paint(SemanticTokenTypes.Keyword, identity_refContext.start);
      }
      super.enterAssignments(ctx);
    }

    @Override
    public void enterGoto_stmt(JimpleParser.Goto_stmtContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.start);
      super.enterGoto_stmt(ctx);
    }

    @Override
    public void enterFile_type(JimpleParser.File_typeContext ctx) {
      if( ctx.getText().charAt(0) == 'c') {
        paint(SemanticTokenTypes.Class, ctx);
      }else{
        paint(SemanticTokenTypes.Interface, ctx);
      }
      super.enterFile_type(ctx);
    }

    @Override
    public void enterExtends_clause(JimpleParser.Extends_clauseContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.start );
      super.enterExtends_clause(ctx);
    }

    @Override
    public void enterImplements_clause(JimpleParser.Implements_clauseContext ctx) {
           paint(SemanticTokenTypes.Keyword, ctx.start );
      super.enterImplements_clause(ctx);
    }

    @Override
    public void enterImportItem(JimpleParser.ImportItemContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.start);
      super.enterImportItem(ctx);
    }

    @Override
    public void enterInvoke_expr(JimpleParser.Invoke_exprContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.start);
      super.enterInvoke_expr(ctx);
    }
  }
}
