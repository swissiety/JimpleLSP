package com.github.swissiety.jimplelsp.languagefeatures;

import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.JimpleParser;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

public class JimpleDocumentSymbol extends JimpleBaseListener {

  // FIXME: ms started this functionality with semantic highlighting in mind.. recheck!

  List<DocumentSymbol> symbols = new ArrayList<>();

  private Range convertRange(Token start, Token stop) {
    return new Range(
            new Position(start.getLine(), start.getCharPositionInLine()),
            new Position(stop.getLine(), stop.getCharPositionInLine()));
  }

  @Override
  public void enterImportItem(JimpleParser.ImportItemContext ctx) {
    symbols.add(
            new DocumentSymbol(
                    ctx.location.getText(),
                    SymbolKind.File,
                    convertRange(ctx.start, ctx.stop),
                    convertRange(ctx.start, ctx.stop)));
  }

  @Override
  public void enterFile(JimpleParser.FileContext ctx) {
    final SymbolKind symbolkind =
            ctx.file_type().getText().equalsIgnoreCase("class")
                    ? SymbolKind.Class
                    : SymbolKind.Interface;
    symbols.add(
            new DocumentSymbol(
                    ctx.classname.getText(),
                    symbolkind,
                    convertRange(ctx.start, ctx.stop),
                    convertRange(ctx.start, ctx.stop)));
  }

  @Override
  public void enterField(JimpleParser.FieldContext ctx) {
    symbols.add(
            new DocumentSymbol(
                    ctx.IDENTIFIER().getText(),
                    SymbolKind.Field,
                    convertRange(ctx.start, ctx.stop),
                    convertRange(ctx.start, ctx.stop)));
  }

  @Override
  public void enterMethod(JimpleParser.MethodContext ctx) {
    // returntype
    symbols.add(
            new DocumentSymbol(
                    ctx.type().getText(),
                    SymbolKind.Object,
                    convertRange(ctx.start, ctx.stop),
                    convertRange(ctx.start, ctx.stop)));
    // method name
    symbols.add(
            new DocumentSymbol(
                    ctx.method_name().getText(),
                    SymbolKind.Method,
                    convertRange(ctx.start, ctx.stop),
                    convertRange(ctx.start, ctx.stop)));
    // parameter types
    if (!ctx.type_list().isEmpty()) {
      for (JimpleParser.TypeContext typeCtx : ctx.type_list().type()) {
        symbols.add(
                new DocumentSymbol(
                        ctx.type().getText(),
                        SymbolKind.Object,
                        convertRange(typeCtx.start, typeCtx.stop),
                        convertRange(typeCtx.start, typeCtx.stop)));
      }
    }
    // throws types
    if (!ctx.throws_clause().isEmpty()) {
      for (JimpleParser.TypeContext typeCtx : ctx.throws_clause().type_list().type()) {
        symbols.add(
                new DocumentSymbol(
                        ctx.type().getText(),
                        SymbolKind.Object,
                        convertRange(typeCtx.start, typeCtx.stop),
                        convertRange(typeCtx.start, typeCtx.stop)));
      }
    }
  }

  @Override
  public void enterImmediate(JimpleParser.ImmediateContext ctx) {
    if (ctx.local != null) {
      symbols.add(
              new DocumentSymbol(
                      ctx.local.getText(),
                      SymbolKind.Variable,
                      convertRange(ctx.start, ctx.stop),
                      convertRange(ctx.start, ctx.stop)));
    }
  }

  @Override
  public void enterConstant(JimpleParser.ConstantContext ctx) {

    SymbolKind symbolKind = SymbolKind.Constant;
    if (ctx.STRING_CONSTANT() != null) {
      if (ctx.CLASS() == null) {
        symbolKind = SymbolKind.String;
      } else {
        symbolKind = SymbolKind.Class;
      }
    } else if (ctx.integer_constant() != null || ctx.FLOAT_CONSTANT() != null) {
      symbolKind = SymbolKind.Number;
    } else if (ctx.NULL() != null) {
      symbolKind = SymbolKind.Null;
    }

    symbols.add(
            new DocumentSymbol(
                    ctx.getText(),
                    symbolKind,
                    convertRange(ctx.start, ctx.stop),
                    convertRange(ctx.start, ctx.stop)));
  }

  @Override
  public void enterBinop(JimpleParser.BinopContext ctx) {
    symbols.add(
            new DocumentSymbol(
                    ctx.getText(),
                    SymbolKind.Operator,
                    convertRange(ctx.start, ctx.stop),
                    convertRange(ctx.start, ctx.stop)));
  }

  @Override
  public void enterUnop(JimpleParser.UnopContext ctx) {
    symbols.add(
            new DocumentSymbol(
                    ctx.getText(),
                    SymbolKind.Operator,
                    convertRange(ctx.start, ctx.stop),
                    convertRange(ctx.start, ctx.stop)));
  }

  @Override
  public void enterStmt(JimpleParser.StmtContext ctx) {

    // TODO

    final JimpleParser.AssignmentsContext assignments = ctx.assignments();
    if (!assignments.isEmpty()) {
      //      symbols.add(new DocumentSymbol(assignments.getText(), SymbolKind.Operator,
      // convertRange(assignments.start, assignments.stop), convertRange(assignments.start,
      // assignments.stop)));
    }
  }
}
