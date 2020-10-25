package magpiebridge.jimplelsp.provider;

import com.google.common.util.concurrent.RateLimiter;
import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.JimpleParser;
import magpiebridge.jimplelsp.JimpleConverterUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import javax.annotation.Nonnull;
import javax.swing.event.DocumentListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JimpleLabelLinkProvider extends JimpleBaseListener {
  @Nonnull
  private final Map<String, Range> labelTargets = new HashMap<>();
  @Nonnull
  private final List<Pair<String, Range>> labelUsage = new ArrayList<>();

  @Nonnull
  public List<DocumentLink> getLinks(String fileUri) {
    List<DocumentLink> links = new ArrayList<>();
    for (Pair<String, Range> usage : labelUsage) {
      final Range position = labelTargets.get(usage.getLeft());
      String target = fileUri + ":" + position.getStart().getLine();
      links.add(new DocumentLink(usage.getRight(), target));
    }
    return links;
  }

  @Override
  public void enterStatement(JimpleParser.StatementContext ctx) {
    if (ctx.label_name != null && ctx.label_name.getText().length() > 0) {
      labelTargets.put(ctx.label_name.getText(), JimpleConverterUtil.buildRangeFromCtx(ctx));
    }
    super.enterStatement(ctx);
  }


  @Override
  public void enterGoto_stmt(JimpleParser.Goto_stmtContext ctx) {
    final JimpleParser.IdentifierContext labelCtx = ctx.label_name;
    if (labelCtx != null) {
      labelUsage.add(Pair.of(labelCtx.getText(), JimpleConverterUtil.buildRangeFromCtx(labelCtx)));
    }
  }

  @Override
  public void enterTrap_clause(JimpleParser.Trap_clauseContext ctx) {
    final JimpleParser.IdentifierContext from = ctx.from;
    if (from != null) {
      labelUsage.add(Pair.of(from.getText(), JimpleConverterUtil.buildRangeFromCtx(from)));
    }
    final JimpleParser.IdentifierContext to = ctx.to;
    if (to != null) {
      labelUsage.add(Pair.of(to.getText(), JimpleConverterUtil.buildRangeFromCtx(to)));
    }
    final JimpleParser.IdentifierContext with = ctx.with;
    if (with != null) {
      labelUsage.add(Pair.of(with.getText(), JimpleConverterUtil.buildRangeFromCtx(with)));
    }
    super.enterTrap_clause(ctx);
  }
}
