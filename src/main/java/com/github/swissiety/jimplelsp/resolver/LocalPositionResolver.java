package com.github.swissiety.jimplelsp.resolver;

import com.github.swissiety.jimplelsp.Util;
import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.jimple.Jimple;
import de.upb.swt.soot.core.model.Position;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.model.SootMethod;
import de.upb.swt.soot.core.signatures.MethodSubSignature;
import de.upb.swt.soot.core.types.Type;
import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.JimpleParser;
import de.upb.swt.soot.jimple.parser.JimpleConverterUtil;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;

/**
 * The LocalResolver handles gathering and queriing for Local Positions in a given File.
 *
 * @author Markus Schmidt
 */
public class LocalPositionResolver {
  @Nonnull final Path path;

  @Nonnull
  private final Map<MethodSubSignature, List<Pair<Position, String>>> localsOfMethod =
      new HashMap<>();

  @Nonnull private final Map<MethodSubSignature, Map<String, Type>> localToType = new HashMap<>();

  public LocalPositionResolver(Path path, ParseTree parseTree) {
    this.path = path;

    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(new LocalDeclarationFinder(path), parseTree);
  }

  @Nullable
  private List<Pair<Position, String>> getLocals(SootClass<?> sc, org.eclipse.lsp4j.Position pos) {
    final Optional<? extends SootMethod> surroundingMethod = getSootMethodFromPosition(sc, pos);
    return surroundingMethod
        .map(sootMethod -> localsOfMethod.get(sootMethod.getSubSignature()))
        .orElse(null);
  }

  @Nonnull
  private Optional<? extends SootMethod> getSootMethodFromPosition(
      @Nonnull SootClass<?> sc, @Nonnull org.eclipse.lsp4j.Position pos) {
    return sc.getMethods().stream().filter(m -> isInRangeOf(pos, m.getPosition())).findAny();
  }

  private Optional<Pair<Position, String>> getSelectedLocalInfo(
      @Nonnull List<Pair<Position, String>> locals, @Nonnull org.eclipse.lsp4j.Position pos) {
    // determine name/range of selected local
    return locals.stream().filter(p -> isInRangeOf(pos, p.getLeft())).findAny();
  }

  @Nonnull
  public List<? extends Location> resolveReferences(SootClass<?> sc, TextDocumentPositionParams pos) {
    List<Pair<Position, String>> locals = getLocals(sc, pos.getPosition());
    if (locals == null) {
      return Collections.emptyList();
    }
    final Optional<Pair<Position, String>> localOpt =
        getSelectedLocalInfo(locals, pos.getPosition());
    if (!localOpt.isPresent()) {
      return Collections.emptyList();
    }
    final String localname = localOpt.get().getRight();
    List<Location> list = new ArrayList<>();
    for (Pair<Position, String> l : locals) {
      if (l.getRight().equals(localname)) {
        Location location = Util.positionToLocation(pos.getTextDocument().getUri(), l.getLeft());
        list.add(location);
      }
    }
    return list;
  }

  @Nullable
  public Type resolveTypeDefinition(
      @Nonnull SootClass<?> sc, @Nonnull org.eclipse.lsp4j.Position pos) {
    final Optional<? extends SootMethod> surroundingMethod = getSootMethodFromPosition(sc, pos);
    if (!surroundingMethod.isPresent()) {
      return null;
    }
    final SootMethod sm = surroundingMethod.get();
    List<Pair<Position, String>> locals = localsOfMethod.get(sm.getSubSignature());
    if (locals == null) {
      return null;
    }

    final Optional<Pair<Position, String>> localOpt = getSelectedLocalInfo(locals, pos);
    if (!localOpt.isPresent()) {
      return null;
    }
    final String localname = localOpt.get().getRight();

    final Map<String, Type> stringTypeMap = localToType.get(sm.getSubSignature());
    if (stringTypeMap != null) {
      return stringTypeMap.get(localname);
    }
    return null;
  }

  @Nullable
  public LocationLink resolveDefinition(
      @Nonnull SootClass<?> sc, @Nonnull TextDocumentPositionParams pos) {
    List<Pair<Position, String>> locals = getLocals(sc, pos.getPosition());
    if (locals == null) {
      return null;
    }
    final Optional<Pair<Position, String>> localOpt =
        getSelectedLocalInfo(locals, pos.getPosition());
    if (!localOpt.isPresent()) {
      return null;
    }
    Pair<Position, String> positionStringPair = localOpt.get();
    final String localname = positionStringPair.getRight();
    // first occurence of that local (in the current method) is the definition (or declaration if
    // existing).
    final Optional<Pair<Position, String>> deflocalOpt =
        locals.stream().filter(p -> p.getRight().equals(localname)).findFirst();
    if (deflocalOpt.isPresent()) {
      Location location = Util.positionToDefLocation(
              pos.getTextDocument().getUri(), deflocalOpt.get().getLeft());
      location.getRange().setEnd( new org.eclipse.lsp4j.Position(deflocalOpt.get().getLeft().getLastLine(), deflocalOpt.get().getLeft().getFirstCol()+deflocalOpt.get().getRight().length() ));
      return new LocationLink( location.getUri(), location.getRange(), location.getRange(), Util.positionToRange(positionStringPair.getLeft()) );
    }
    return null;
  }

  private boolean isInRangeOf(@Nonnull org.eclipse.lsp4j.Position p1, @Nonnull Position p2) {
    if (p1.getLine() < p2.getFirstLine()) {
      return false;
    } else if (p1.getLine() == p2.getFirstLine() && p1.getCharacter() < p2.getFirstCol()) {
      return false;
    }

    if (p1.getLine() > p2.getLastLine()) {
      return false;
    } else if (p1.getLine() == p2.getLastLine() && p1.getCharacter() > p2.getLastCol()) {
      return false;
    }

    return true;
  }

  private final class LocalDeclarationFinder extends JimpleBaseListener {
    private final Path path;
    private final JimpleConverterUtil util;

    private MethodSubSignature currentMethodSig = null;
    private List<Pair<Position, String>> currentLocalPositionList = null;
    private Map<String, Type> currentLocalToType = null;

    private LocalDeclarationFinder(@Nonnull Path path) {
      this.path = path;
      util = new JimpleConverterUtil(path);
    }

    @Override
    public void enterMethod(JimpleParser.MethodContext ctx) {
      currentMethodSig = util.getMethodSubSignature(ctx.method_subsignature(), ctx);
      currentLocalPositionList = new ArrayList<>();
      currentLocalToType = new HashMap<>();
      super.enterMethod(ctx);
    }

    @Override
    public void exitMethod(JimpleParser.MethodContext ctx) {
      localsOfMethod.put(currentMethodSig, currentLocalPositionList);
      localToType.put(currentMethodSig, currentLocalToType);
    }

    @Override
    public void enterDeclaration(JimpleParser.DeclarationContext ctx) {
      final JimpleParser.Arg_listContext arg_listCtx = ctx.arg_list();
      if (arg_listCtx == null) {
        throw new ResolveException(
            "Jimple Syntaxerror: Locals are missing.",
            path,
            JimpleConverterUtil.buildPositionFromCtx(ctx));
      }
      if (ctx.type() == null) {
        throw new ResolveException(
            "Jimple Syntaxerror: Type missing.",
            path,
            JimpleConverterUtil.buildPositionFromCtx(ctx));
      }

      final Type type = util.getType(ctx.type().getText());
      for (JimpleParser.ImmediateContext immediateCtx : arg_listCtx.immediate()) {
        // remember type
        currentLocalToType.put(Jimple.unescape(immediateCtx.local.getText()), type);
      }

      super.enterDeclaration(ctx);
    }

    @Override
    public void enterAssignments(JimpleParser.AssignmentsContext ctx) {
      if (ctx.local != null) {
        currentLocalPositionList.add(
            Pair.of(
                JimpleConverterUtil.buildPositionFromCtx(ctx.local),
                Jimple.unescape(ctx.local.getText())));
      }
      super.enterAssignments(ctx);
    }

    @Override
    public void enterReference(JimpleParser.ReferenceContext ctx) {
      if (ctx.identifier() != null) {
        currentLocalPositionList.add(
            Pair.of(
                JimpleConverterUtil.buildPositionFromCtx(ctx.identifier()),
                Jimple.unescape(ctx.identifier().getText())));
      }
      super.enterReference(ctx);
    }

    @Override
    public void enterInvoke_expr(JimpleParser.Invoke_exprContext ctx) {
      if (ctx.local_name != null) {
        currentLocalPositionList.add(
            Pair.of(
                JimpleConverterUtil.buildPositionFromCtx(ctx.local_name),
                Jimple.unescape(ctx.local_name.getText())));
      }
      super.enterInvoke_expr(ctx);
    }

    @Override
    public void enterImmediate(JimpleParser.ImmediateContext ctx) {
      if (ctx.local != null) {
        currentLocalPositionList.add(
            Pair.of(
                JimpleConverterUtil.buildPositionFromCtx(ctx.local),
                Jimple.unescape(ctx.local.getText())));
      }
      super.enterImmediate(ctx);
    }

    @Override
    public void enterImportItem(JimpleParser.ImportItemContext ctx) {
      util.addImport(ctx);
      super.enterImportItem(ctx);
    }
  }
}
