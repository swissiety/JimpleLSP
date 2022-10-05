package com.github.swissiety.jimplelsp.provider;

import com.github.swissiety.jimplelsp.Util;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.JimpleParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.jheaps.annotations.Beta;

/**
 * This listener implementation lists all label usage positions when its passed into the Jimple
 * Parser
 *
 * @author Markus Schmidt
 */
@Beta
public class JimpleLabelProvider extends JimpleBaseListener {
  @Nonnull private final Map<String, Range> labelTargets = new HashMap<>();
  @Nonnull private final List<Pair<String, Range>> labelUsage = new ArrayList<>();

  @Nonnull
  public void resolve(@Nonnull SootClass sc, @Nonnull TextDocumentPositionParams pos) {
    // TODO: implement label indexing
  }

  @Override
  public void enterStatement(JimpleParser.StatementContext ctx) {
    // add label preceeding a stmt as target into the map
    if (ctx.label_name != null) {
      String text = ctx.label_name.getText();
      if (text.length() > 0) {
        labelTargets.put(text, Util.ctxToRange(ctx));
      }
    }
    super.enterStatement(ctx);
  }

  @Override
  public void enterGoto_stmt(JimpleParser.Goto_stmtContext ctx) {
    // adds the labels of all branching stmts (goto,if,switch) -> JimpleParser.Goto_stmtContext is
    // part of all of these stmts!
    final JimpleParser.IdentifierContext labelCtx = ctx.label_name;
    if (labelCtx != null) {
      labelUsage.add(Pair.of(labelCtx.getText(), Util.ctxToRange(labelCtx)));
    }
    super.enterGoto_stmt(ctx);
  }

  @Override
  public void enterTrap_clause(JimpleParser.Trap_clauseContext ctx) {
    // add labes usage from catch clause
    final JimpleParser.IdentifierContext from = ctx.from;
    if (from != null) {
      labelUsage.add(Pair.of(from.getText(), Util.ctxToRange(from)));
    }
    final JimpleParser.IdentifierContext to = ctx.to;
    if (to != null) {
      labelUsage.add(Pair.of(to.getText(), Util.ctxToRange(to)));
    }
    final JimpleParser.IdentifierContext with = ctx.with;
    if (with != null) {
      labelUsage.add(Pair.of(with.getText(), Util.ctxToRange(with)));
    }
    super.enterTrap_clause(ctx);
  }
}
