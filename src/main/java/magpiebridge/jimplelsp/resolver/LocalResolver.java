package magpiebridge.jimplelsp.resolver;

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
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import magpiebridge.jimplelsp.Util;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class LocalResolver {
  @Nonnull final Path path;
  @Nonnull private final Map<MethodSubSignature, List<Pair<Position, String>>> localsOfMethod =
      new HashMap<>();
  @Nonnull private final Map<MethodSubSignature, Map<String, Type>> localToType = new HashMap<>();

  public LocalResolver(Path path) {
    this.path = path;
    final CharStream charStream;
    try {
      charStream = CharStreams.fromPath(this.path);
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    final JimpleParser jimpleParser = JimpleConverterUtil.createJimpleParser(charStream, this.path);
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(new LocalDeclarationFinder(path), jimpleParser.file());
  }

  @Nullable
  public Type resolveTypeDefinition(@Nonnull SootClass sc, @Nonnull TextDocumentPositionParams pos) {
    final Optional<SootMethod> surroundingMethod =
        sc.getMethods().stream()
            .filter(m -> isInRangeOf(pos.getPosition(), m.getPosition()))
            .findAny();
    if (!surroundingMethod.isPresent()) {
      return null;
    }
    final SootMethod sm = surroundingMethod.get();
    List<Pair<Position, String>> locals = localsOfMethod.get(sm.getSubSignature());
    if(locals == null){
      return null;
    }
    // determine name/range of local
    final Optional<Pair<Position, String>> localOpt = locals.stream().filter(p -> isInRangeOf(pos.getPosition(), p.getLeft())).findAny();
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
  public Either<List<? extends Location>, List<? extends LocationLink>> resolveDefinition(
          @Nonnull SootClass sc, @Nonnull TextDocumentPositionParams pos) {
    final Optional<SootMethod> surroundingMethod =
        sc.getMethods().stream()
            .filter(m -> isInRangeOf(pos.getPosition(), m.getPosition()))
            .findAny();
    if (!surroundingMethod.isPresent()) {
      return null;
    }
    List<Pair<Position, String>> locals =
        localsOfMethod.get(surroundingMethod.get().getSubSignature());
    if(locals == null){
      return null;
    }
    // determine name/range of local
    final Optional<Pair<Position, String>> localOpt = locals.stream().filter(p1 -> isInRangeOf(pos.getPosition(), p1.getLeft())).findAny();
    if (!localOpt.isPresent()) {
      return null;
    }
    final String localname = localOpt.get().getRight();
    // first occurence of that local (in the current method) is the definition (or declaration if
    // existing).
    final Optional<Pair<Position, String>> deflocalOpt =
        locals.stream().filter(p -> p.getRight().equals(localname)).findFirst();
    if (deflocalOpt.isPresent()) {
      return Either.forLeft(
          Collections.singletonList(
              Util.positionToLocation(
                  pos.getTextDocument().getUri(), deflocalOpt.get().getLeft())));
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
