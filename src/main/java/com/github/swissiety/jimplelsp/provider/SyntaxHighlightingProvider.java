package com.github.swissiety.jimplelsp.provider;

import de.upb.swt.soot.jimple.JimpleBaseVisitor;
import de.upb.swt.soot.jimple.JimpleParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/** @author Markus Schmidt */
public class SyntaxHighlightingProvider {

  @Nonnull
  static final SemanticTokensLegend legend =
      new SemanticTokensLegend(
          Arrays.asList(
              SemanticTokenTypes.Keyword,
              SemanticTokenTypes.Comment,
              SemanticTokenTypes.Modifier,
              SemanticTokenTypes.Class,
              SemanticTokenTypes.Method,
              SemanticTokenTypes.Type,
              SemanticTokenTypes.Variable,
              SemanticTokenTypes.Parameter,
              SemanticTokenTypes.String,
              SemanticTokenTypes.Number,
              SemanticTokenTypes.Operator,
              SemanticTokenTypes.Interface),
          Collections.emptyList());

  public static SemanticTokensLegend getLegend() {
    return legend;
  }

  public static SemanticTokens paintbrush(@Nonnull ParseTree parseTree) throws IOException {

    SemanticTokenManager semanticTokenManager = new SemanticTokenManager(legend);
    new SyntaxHighlightingVisitor(semanticTokenManager).visit(parseTree);
    return new SemanticTokens(semanticTokenManager.getCanvas());
  }

  /** 'Cause colors make the world go round */
  private static class SyntaxHighlightingVisitor extends JimpleBaseVisitor<SemanticTokenManager> {

    @Nonnull private final SemanticTokenManager semanticTokenManager;

    private SyntaxHighlightingVisitor(@Nonnull SemanticTokenManager semanticTokenManager) {
      this.semanticTokenManager = semanticTokenManager;
    }

    private void paint(@Nonnull String tokentype, @Nonnull Token token) {
      // TODO: add tokenModifier
      // zero offset line/column
      semanticTokenManager.paintText(
          tokentype,
          "",
          token.getLine() - 1,
          token.getCharPositionInLine(),
          token.getText().length());
    }

    private void paint(@Nonnull String tokentype, @Nonnull ParserRuleContext ctx) {
      // TODO: add tokenModifier
      // zero offset line/column
      String tokenText = ctx.getText();

      semanticTokenManager.paintText(
          tokentype,
          "",
          ctx.start.getLine() - 1,
          ctx.start.getCharPositionInLine(),
          ctx.getText().length());
    }

    @Override
    public SemanticTokenManager visitFile(JimpleParser.FileContext ctx) {
      ctx.modifier().forEach(x -> paint(SemanticTokenTypes.Modifier, x));
      visitFile_type(ctx.file_type());
      if (ctx.file_type().getText().charAt(0) == 'c') {
        paint(SemanticTokenTypes.Class, ctx.classname);
      } else {
        paint(SemanticTokenTypes.Interface, ctx.classname);
      }

      JimpleParser.Extends_clauseContext extendsClauseCtx = ctx.extends_clause();
      if (extendsClauseCtx != null) {
        visitExtends_clause(extendsClauseCtx);
      }

      JimpleParser.Implements_clauseContext implements_clauseContext = ctx.implements_clause();
      if (implements_clauseContext != null) {
        visitImplements_clause(implements_clauseContext);
      }
      ctx.member().forEach(this::visitMember);
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitFile_type(JimpleParser.File_typeContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx);
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitImportItem(JimpleParser.ImportItemContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.start);
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitExtends_clause(JimpleParser.Extends_clauseContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.start);
      paint(SemanticTokenTypes.Type, ctx.identifier());
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitImplements_clause(JimpleParser.Implements_clauseContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.start);
      JimpleParser.Type_listContext typeList = ctx.type_list();
      if (typeList != null) {
        visitType_list(typeList);
      }
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitField(JimpleParser.FieldContext ctx) {
      ctx.modifier().forEach(this::visitModifier);
      paint(SemanticTokenTypes.Type, ctx.type());
      paint(SemanticTokenTypes.Variable, ctx.identifier());
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitMethod(JimpleParser.MethodContext ctx) {
      ctx.modifier().forEach(this::visitModifier);
      visitMethod_subsignature(ctx.method_subsignature());
      JimpleParser.Throws_clauseContext throws_clauseContext = ctx.throws_clause();
      if (throws_clauseContext != null) {
        paint(SemanticTokenTypes.Keyword, throws_clauseContext.start); // fix .g4 to use THROWS
        visitType_list(throws_clauseContext.type_list());
      }
      visitMethod_body(ctx.method_body());
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitTrap_clause(JimpleParser.Trap_clauseContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.CATCH().getSymbol());
      paint(SemanticTokenTypes.Type, ctx.exceptiontype);
      paint(SemanticTokenTypes.Keyword, ctx.FROM().getSymbol());
      paint(SemanticTokenTypes.Keyword, ctx.TO().getSymbol());
      paint(SemanticTokenTypes.Keyword, ctx.WITH().getSymbol());

      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitType(JimpleParser.TypeContext ctx) {
      paint(SemanticTokenTypes.Type, ctx.start);
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitModifier(JimpleParser.ModifierContext ctx) {
      paint(SemanticTokenTypes.Modifier, ctx);
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitMethod_signature(JimpleParser.Method_signatureContext ctx) {
      // paint(SemanticTokenTypes.Variable, ctx.identifier());
      paint(SemanticTokenTypes.Type, ctx.class_name);
      visitMethod_subsignature(ctx.method_subsignature());
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitMethod_subsignature(
        JimpleParser.Method_subsignatureContext ctx) {
      visitType(ctx.type());
      paint(SemanticTokenTypes.Method, ctx.method_name());
      if (ctx.type_list() != null) {
        visitType_list(ctx.type_list());
      }
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitField_signature(JimpleParser.Field_signatureContext ctx) {
      paint(SemanticTokenTypes.Type, ctx.classname);
      paint(SemanticTokenTypes.Type, ctx.type());
      paint(SemanticTokenTypes.Variable, ctx.fieldname);
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitReference(JimpleParser.ReferenceContext ctx) {
      JimpleParser.IdentifierContext identifier = ctx.identifier();
      if (identifier != null) {
        paint(SemanticTokenTypes.Variable, identifier);
        final JimpleParser.Array_descriptorContext arrayDescrCtx = ctx.array_descriptor();
        if (arrayDescrCtx != null) {
          visitArray_descriptor(arrayDescrCtx);
        }
      }

      final JimpleParser.Field_signatureContext ctx1 = ctx.field_signature();
      if (ctx1 != null) {
        visitField_signature(ctx1);
      }

      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitImmediate(JimpleParser.ImmediateContext ctx) {
      if (ctx.local != null) {
        paint(SemanticTokenTypes.Variable, ctx);
      } else {
        visitConstant(ctx.constant());
      }
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitConstant(JimpleParser.ConstantContext ctx) {
      String text = ctx.getText();
      if (text.charAt(0) == '"' || text.charAt(0) == '\'') {
        paint(SemanticTokenTypes.String, ctx);
      } else if (ctx.CLASS() != null) {
        paint(SemanticTokenTypes.Keyword, ctx.CLASS().getSymbol());
        paint(SemanticTokenTypes.Type, ctx.identifier());
      } else {
        // number, boolean, ..
        paint(SemanticTokenTypes.Number, ctx);
      }
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitStmt(JimpleParser.StmtContext ctx) {
      if (ctx.IF() != null
              || ctx.RETURN() != null
              || ctx.ENTERMONITOR() != null
              || ctx.EXITMONITOR() != null
              || ctx.BREAKPOINT() != null
              || ctx.THROW() != null
              || ctx.NOP() != null) {
        paint(SemanticTokenTypes.Keyword, ctx.start);
        if (ctx.immediate() != null) {
          visitImmediate(ctx.immediate());
        }
      } else if (ctx.assignments() != null) {
        visitAssignments(ctx.assignments());
      } else if (ctx.goto_stmt() != null) {
        visitGoto_stmt(ctx.goto_stmt());
      } else if (ctx.SWITCH() != null) {
        paint(SemanticTokenTypes.Keyword, ctx.start);
        ctx.case_stmt().forEach(this::visitCase_stmt);
      }

      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitCase_stmt(JimpleParser.Case_stmtContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.start);
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitCase_label(JimpleParser.Case_labelContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.start);
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitDeclaration(JimpleParser.DeclarationContext ctx) {
      paint(SemanticTokenTypes.Type, ctx.type());
      visitArg_list(ctx.arg_list());
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitAssignments(JimpleParser.AssignmentsContext ctx) {
      JimpleParser.Identity_refContext identity_refContext = ctx.identity_ref();
      if (identity_refContext != null) {
        paint(SemanticTokenTypes.Variable, ctx.identifier());
        paint(SemanticTokenTypes.Keyword, identity_refContext.start);
        if (identity_refContext.DEC_CONSTANT() != null) {
          paint(SemanticTokenTypes.Keyword, identity_refContext.DEC_CONSTANT().getSymbol());
        }
      } else {

        if (ctx.reference() != null) {
          visitReference(ctx.reference());
        } else {
          paint(SemanticTokenTypes.Variable, ctx.identifier());
        }
        visitValue(ctx.value());
      }
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitGoto_stmt(JimpleParser.Goto_stmtContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.start);
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitArg_list(JimpleParser.Arg_listContext ctx) {
      for (JimpleParser.ImmediateContext immediateContext : ctx.immediate()) {
        paint(SemanticTokenTypes.Variable, immediateContext);
      }
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitInvoke_expr(JimpleParser.Invoke_exprContext ctx) {
      paint(SemanticTokenTypes.Keyword, ctx.start);

      if (ctx.identifier() != null) {
        paint(SemanticTokenTypes.Variable, ctx.identifier());
      }

      if (ctx.DYNAMICINVOKE() != null) {

        if (ctx.type() != null) {
          visitType(ctx.type());
        }
        if (ctx.type_list() != null) {
          visitType_list(ctx.type_list());
        }
        if (ctx.dyn_args != null) {
          ctx.dyn_args.immediate().forEach(this::visitImmediate);
        }

        visitMethod_signature(ctx.method_signature());

        if (ctx.staticargs != null) {
          ctx.staticargs.immediate().forEach(this::visitImmediate);
        }
      } else {
        visitMethod_signature(ctx.method_signature());

        if (ctx.arg_list() != null && !ctx.arg_list().isEmpty()) {
          visitArg_list(ctx.arg_list(0));
        }
      }

      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitValue(JimpleParser.ValueContext ctx) {
      if (ctx.NEW() != null) {
        paint(SemanticTokenTypes.Keyword, ctx.NEW().getSymbol());
        paint(SemanticTokenTypes.Type, ctx.identifier());
      } else if (ctx.NEWARRAY() != null) {
        paint(SemanticTokenTypes.Keyword, ctx.NEWARRAY().getSymbol());
        paint(SemanticTokenTypes.Type, ctx.type());
      } else if (ctx.NEWMULTIARRAY() != null) {
        paint(SemanticTokenTypes.Keyword, ctx.NEWMULTIARRAY().getSymbol());
        paint(SemanticTokenTypes.Type, ctx.type());
      } else if (ctx.INSTANCEOF() != null) {
        paint(SemanticTokenTypes.Variable, ctx.op);
        paint(SemanticTokenTypes.Keyword, ctx.INSTANCEOF().getSymbol());
        paint(SemanticTokenTypes.Type, ctx.type());
      } else {
        visitChildren(ctx);
      }
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitBinop(JimpleParser.BinopContext ctx) {
      paint(SemanticTokenTypes.Operator, ctx);
      return semanticTokenManager;
    }

    @Override
    public SemanticTokenManager visitUnop(JimpleParser.UnopContext ctx) {
      paint(SemanticTokenTypes.Operator, ctx);
      return semanticTokenManager;
    }
  }
}
