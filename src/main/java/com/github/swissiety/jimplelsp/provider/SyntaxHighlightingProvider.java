package com.github.swissiety.jimplelsp.provider;

import com.github.swissiety.jimplelsp.Util;
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
    //final SemanticTokenManager semanticTokenManager;

    public static SemanticTokensLegend createLegend() {
        SemanticTokenTypeEnum[] tokenTypes = new SemanticTokenTypeEnum[]{SemanticTokenTypeEnum.Keyword, SemanticTokenTypeEnum.Comment, SemanticTokenTypeEnum.Modifier, SemanticTokenTypeEnum.Class, SemanticTokenTypeEnum.Member, SemanticTokenTypeEnum.Type, SemanticTokenTypeEnum.Variable, SemanticTokenTypeEnum.Parameter, SemanticTokenTypeEnum.String, SemanticTokenTypeEnum.Number};
        return new SemanticTokensLegend(Arrays.stream(tokenTypes).map(SemanticTokenTypeEnum::toString).collect(Collectors.toList()), Collections.emptyList());
    }

    public SyntaxHighlightingProvider(@Nonnull SemanticTokensLegend legend) {
      //  semanticTokenManager = new SemanticTokenManager(legend);
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

        return null; // FIXME: new SemanticTokens(semanticTokenManager.getCanvas());
    }

    public SemanticTokensLegend getLegend() {
        return null; // FIXME semanticTokenManager.getLegend();
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
            // FIXME semanticTokenManager.paintText(tokentype, null, token.getLine() - 1, token.getCharPositionInLine(), token.getText().length());
        }

        private void paint(SemanticTokenTypeEnum tokentype, ParserRuleContext ctx) {
            // TODO: add tokenModifier
            // zero offset line/column
            // FIXME: semanticTokenManager.paintText(tokentype, null, ctx.start.getLine() - 1, ctx.start.getCharPositionInLine(), ctx.getText().length());
        }


        @Override
        public void enterIdentifier(JimpleParser.IdentifierContext ctx) {
            paint(SemanticTokenTypeEnum.Variable, ctx);
            super.enterIdentifier(ctx);
        }

        @Override
        public void enterAssignments(JimpleParser.AssignmentsContext ctx) {
            //paintTokenTypeEnum.Variable, ctx);
            super.enterAssignments(ctx);
        }

        @Override
        public void enterExtends_clause(JimpleParser.Extends_clauseContext ctx) {
            // TODO: keyword
//     paintTokenTypeEnum.Keyword, ctx);
            super.enterExtends_clause(ctx);
        }

        @Override
        public void enterImplements_clause(JimpleParser.Implements_clauseContext ctx) {
            // TODO: keyword
//     paintTokenTypeEnum.Keyword, ctx);
            super.enterImplements_clause(ctx);
        }

        @Override
        public void enterImportItem(JimpleParser.ImportItemContext ctx) {
            // TODO: keyword
//     paintTokenTypeEnum.Keyword, ctx);
            super.enterImportItem(ctx);
        }

        @Override
        public void enterInvoke_expr(JimpleParser.Invoke_exprContext ctx) {
            if (ctx.dynamicinvoke != null) {
                paint(SemanticTokenTypeEnum.Keyword, ctx.dynamicinvoke);
            } else if (ctx.nonstaticinvoke != null) {

            } else if (ctx.staticinvoke != null) {

            }

            //     paintTokenTypeEnum.Keyword, ctx);
            super.enterInvoke_expr(ctx);
        }
    }
}
