package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.JimpleLexer;
import de.upb.swt.soot.jimple.JimpleParser;
import org.antlr.v4.runtime.*;

import javax.swing.text.Position;
import java.util.ArrayList;
import java.util.List;

public class LocalDeclarationResolver {

/*
  LocalDeclarationResolver() {
    JimpleLexer lexer = new JimpleLexer(CharStreams.fromString(contents));
    TokenStream tokens = new CommonTokenStream(lexer);
    JimpleParser parser = new JimpleParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(new BaseErrorListener() {
      public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException
              e) {
        throw new ResolveException("Jimple Syntaxerror: " + msg, fileUri, new de.upb.swt.soot.core.model.Position(line, charPositionInLine, -1, -1));
      }
    });

    parser.method_body().enterRule(new LocalDeclarationFinder(fileUri));

  }


  private final class LocalDeclarationFinder extends JimpleBaseListener {
    private final String fileUri;
    List<Position> localList = new ArrayList<>();

    private LocalDeclarationFinder(String fileUri) {
      this.fileUri = fileUri;
    }

    @Override
    public void enterDeclaration(JimpleParser.DeclarationContext ctx) {
      final JimpleParser.Arg_listContext arg_listCtx = ctx.arg_list();
      if (arg_listCtx == null) {
        throw new ResolveException("Jimple Syntaxerror: Locals are missing.", fileUri, JimpleConverterUtil.buildPositionFromCtx(ctx) );
      }
      if (ctx.type() == null) {
        throw new ResolveException("Jimple Syntaxerror: Type missing.", fileUri, JimpleConverterUtil.buildPositionFromCtx(ctx) );
      }
      for (JimpleParser.ImmediateContext immediateCtx : arg_listCtx.immediate()) {
        // TODO: immediateCtx.local
      }
      super.enterDeclaration(ctx);
    }

    @Override
    public void enterImmediate(JimpleParser.ImmediateContext ctx) {
      if (ctx.local == null) {
          throw new ResolveException("Jimple Syntaxerror: immediate is not a local.", fileUri, JimpleConverterUtil.buildPositionFromCtx(ctx) );
      }
      // localList.add( ctx.local );

      super.enterImmediate(ctx);
    }
  }*/
}
