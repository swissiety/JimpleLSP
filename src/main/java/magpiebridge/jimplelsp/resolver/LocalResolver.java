package magpiebridge.jimplelsp.resolver;

import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.jimple.Jimple;
import de.upb.swt.soot.core.model.Position;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.model.SootMethod;
import de.upb.swt.soot.core.signatures.MethodSubSignature;
import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.JimpleParser;
import de.upb.swt.soot.jimple.parser.JimpleConverterUtil;
import magpiebridge.jimplelsp.Util;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class LocalResolver {
  final Path path;

  //private final List<Position> localPosList = new ArrayList<>();
  private final Map<MethodSubSignature, List<Pair<Position, String>>> localsOfMethod = new HashMap<>();

  public LocalResolver(Path path) {
    this.path = path;
    final CharStream charStream;
    try {
      charStream = CharStreams.fromPath(this.path);
    } catch (IOException exception) {
      return;
    }

    final JimpleParser jimpleParser = JimpleConverterUtil.createJimpleParser(charStream, this.path);
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(new LocalDeclarationFinder(this.path), jimpleParser.file());
  }

  public Either<List<? extends Location>, List<? extends LocationLink>> resolveDefinition(SootClass sc, TextDocumentPositionParams pos ){
    final Optional<SootMethod> surroundingMethod = sc.getMethods().stream().filter(m -> isInRangeOf( pos.getPosition(), m.getPosition()) ).findAny();
    if (surroundingMethod.isPresent()) {
      final List<Pair<Position, String>> locals = localsOfMethod.get(surroundingMethod.get());
      if(locals == null){
        return null;
      }

      final Optional<Pair<Position, String>> localOpt = locals.stream().findFirst(pair -> isInRangeOf(pos.getPosition(), pair.getLeft()) );
      if (localOpt.isPresent()) {
        return Either.forLeft(Collections.singletonList(Util.positionToLocation(pos.getTextDocument().getUri(),localOpt.get().getLeft())));
      }
    }
    return null;
  }

  private boolean isInRangeOf(org.eclipse.lsp4j.Position p1, Position p2) {
    if(p1.getLine() >= p2.getFirstLine() && p1.getLine() <= p2.getLastLine() ){
     if( p1.getCharacter() >= p2.getFirstCol() && p1.getCharacter() <= p2.getLastCol()){
       return true;
     }
    }
    return false;
  }


  private final class LocalDeclarationFinder extends JimpleBaseListener {
    private final Path path;
    private final JimpleConverterUtil util;

    private MethodSubSignature currentMethodSig = null;
    private List<Pair<Position, String>> currentLocalPositionList = null;

    private LocalDeclarationFinder(Path path) {
      this.path = path;
      util = new JimpleConverterUtil(path);
    }

    @Override
    public void enterMethod(JimpleParser.MethodContext ctx) {
      localsOfMethod.put( currentMethodSig, currentLocalPositionList);
      currentMethodSig = util.getMethodSubSignature(ctx.method_subsignature(), ctx);
      currentLocalPositionList = new ArrayList<>();
      super.enterMethod(ctx);
    }

    @Override
    public void enterDeclaration(JimpleParser.DeclarationContext ctx) {
      final JimpleParser.Arg_listContext arg_listCtx = ctx.arg_list();
      if (arg_listCtx == null) {
        throw new ResolveException("Jimple Syntaxerror: Locals are missing.", path, JimpleConverterUtil.buildPositionFromCtx(ctx) );
      }
      if (ctx.type() == null) {
        throw new ResolveException("Jimple Syntaxerror: Type missing.", path, JimpleConverterUtil.buildPositionFromCtx(ctx) );
      }
      for (JimpleParser.ImmediateContext immediateCtx : arg_listCtx.immediate()) {
        currentLocalPositionList.add( Pair.of(JimpleConverterUtil.buildPositionFromCtx(immediateCtx.local), Jimple.unescape(immediateCtx.local.getText() )) );
      }

      super.enterDeclaration(ctx);
    }

    @Override
    public void enterImmediate(JimpleParser.ImmediateContext ctx) {
      if (ctx.local == null) {
          throw new ResolveException("Jimple Syntaxerror: immediate is not a local.", path, JimpleConverterUtil.buildPositionFromCtx(ctx) );
      }

      currentLocalPositionList.add( Pair.of(JimpleConverterUtil.buildPositionFromCtx(ctx.local), Jimple.unescape(ctx.local.getText())) );

      super.enterImmediate(ctx);
    }
  }
}
