package magpiebridge.jimplelsp.resolver;

import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.signatures.Signature;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.types.Type;
import de.upb.swt.soot.core.util.StringTools;
import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.JimpleLexer;
import de.upb.swt.soot.jimple.parser.JimpleConverterUtil;
import de.upb.swt.soot.jimple.JimpleParser;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.antlr.v4.runtime.*;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Position;

/**
 * This Class holds information about (open) jimple files. Especially range information about
 * occurences of Signature's or even Siganture+Identifier.
 *
 * @author Markus Schmidt
 */
public class SignaturePositionResolver {
  private final SignatureOccurenceAggregator occurences = new SignatureOccurenceAggregator();
  private final String fileUri;

  public SignaturePositionResolver(String fileUri, String contents) {
    this.fileUri = fileUri;

    JimpleLexer lexer = new JimpleLexer(CharStreams.fromString(contents));
    TokenStream tokens = new CommonTokenStream(lexer);
    JimpleParser parser = new JimpleParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(
        new BaseErrorListener() {
          public void syntaxError(
              Recognizer<?, ?> recognizer,
              Object offendingSymbol,
              int line,
              int charPositionInLine,
              String msg,
              RecognitionException e) {
            throw new ResolveException(
                "Jimple Syntaxerror: " + msg,
                fileUri,
                new de.upb.swt.soot.core.model.Position(line, charPositionInLine, -1, -1));
          }
        });

    final SignatureOccurenceAggregator signatureOccurenceAggregator =
        new SignatureOccurenceAggregator();
    parser.file().enterRule(signatureOccurenceAggregator);
  }

  @Nullable
  public Signature resolve(org.eclipse.lsp4j.Position position) {
    return occurences.resolve(position);
  }

  private final class SignatureOccurenceAggregator extends JimpleBaseListener {

    JimpleConverterUtil util = new JimpleConverterUtil(fileUri);
    SmartDatastructure positionContainer = new SmartDatastructure();
    ClassType clazz;

    @Override
    public void enterFile(JimpleParser.FileContext ctx) {
      if (ctx.classname == null) {
        throw new ResolveException(
            "Identifier for this unit is not found.", fileUri, util.buildPositionFromCtx(ctx));
      }
      String classname = StringTools.getUnEscapedStringOf(ctx.classname.getText());
      clazz = util.getClassType(classname);
      positionContainer.add(ctx.classname.start, ctx.classname.stop, clazz, null);

      if (ctx.extends_clause() != null) {
        ClassType superclass = util.getClassType(ctx.extends_clause().classname.getText());
        positionContainer.add(
            ctx.extends_clause().start, ctx.extends_clause().stop, superclass, null);
      }

      if (ctx.implements_clause() != null) {
        List<ClassType> interfaces = util.getClassTypeList(ctx.implements_clause().type_list());
        for (int i = 0, interfacesSize = interfaces.size(); i < interfacesSize; i++) {
          ClassType anInterface = interfaces.get(i);
          final JimpleParser.TypeContext interfaceToken =
              ctx.implements_clause().type_list().type(i);
          positionContainer.add(interfaceToken.start, interfaceToken.stop, anInterface, null);
        }
      }

      super.enterFile(ctx);
    }

    @Override
    public void enterMethod(JimpleParser.MethodContext ctx) {
      // parsing the declaration

      Type type = util.getType(ctx.type().getText());
      if (type == null) {
        throw new ResolveException(
            "Returntype not found.", fileUri, util.buildPositionFromCtx(ctx));
      }
      String methodname = ctx.method_name().getText();
      if (methodname == null) {
        throw new ResolveException(
            "Methodname not found.", fileUri, util.buildPositionFromCtx(ctx));
      }

      List<Type> params = util.getTypeList(ctx.type_list());
      MethodSignature methodSignature =
          util.getIdentifierFactory()
              .getMethodSignature(
                  StringTools.getUnEscapedStringOf(methodname), clazz, type, params);
      positionContainer.add(ctx.start, ctx.stop, methodSignature, null);

      if (ctx.throws_clause() != null) {
        List<ClassType> exceptions = util.getClassTypeList(ctx.throws_clause().type_list());
        for (int i = 0, exceptionsSize = exceptions.size(); i < exceptionsSize; i++) {
          final JimpleParser.TypeContext typeContext = ctx.throws_clause().type_list().type(i);
          positionContainer.add(typeContext.start, typeContext.stop, exceptions.get(i), null);
        }
      }

      super.enterMethod(ctx);
    }

    @Override
    public void enterMethod_signature(JimpleParser.Method_signatureContext ctx) {
      positionContainer.add(ctx.start, ctx.stop, util.getMethodSignature(ctx, null), null);
      super.enterMethod_signature(ctx);
    }

    @Override
    public void enterField_signature(JimpleParser.Field_signatureContext ctx) {
      positionContainer.add(ctx.start, ctx.stop, util.getFieldSignature(ctx), null);
      super.enterField_signature(ctx);
    }

    @Override
    public void enterImportItem(JimpleParser.ImportItemContext ctx) {
      util.addImport(ctx);
      super.enterImportItem(ctx);
    }

    @Nullable
    Signature resolve(org.eclipse.lsp4j.Position position) {
      final Pair<Signature, String> resolve = positionContainer.resolve(position);
      if (resolve == null) {
        return null;
      }
      return resolve.getLeft();
    }
  }

  private final class SmartDatastructure {

    @Nonnull List<Position> startPositions = new ArrayList<>();
    @Nonnull List<Position> endPositions = new ArrayList<>();
    @Nonnull List<Pair<Signature, String>> signaturesAndIdentifiers = new ArrayList<>();

    Comparator<Position> comp = new PositionComparator();

    void add(Token start, Token end, Signature sig, @Nullable String identifier) {
      final Position startPos = new Position(start.getLine(), start.getCharPositionInLine());
      final int idx = Collections.binarySearch(startPositions, startPos, new PositionComparator());
      startPositions.add(idx, startPos);
      endPositions.add(idx, new Position(end.getLine(), end.getCharPositionInLine()));
      signaturesAndIdentifiers.add(idx, Pair.of(sig, identifier));
    }

    @Nullable
    Pair<Signature, String> resolve(org.eclipse.lsp4j.Position position) {
      int index = Collections.binarySearch(startPositions, position, new PositionComparator());

      if (index < 0 || index >= startPositions.size()) {
        // not exactly found;
        index = -index + 1;
      }
      if (comp.compare(startPositions.get(index), position) <= 0
          && comp.compare(position, endPositions.get(index)) <= 0) {
        return signaturesAndIdentifiers.get(index);
      }
      return null;
    }

    // FIXME: implement or dependency
  }

  private static final class PositionComparator implements Comparator<Position> {
    @Override
    public int compare(Position o1, Position o2) {
      if (o1.getLine() < o2.getLine()) {
        return -1;
      } else if (o1.getLine() == o2.getLine()) {
        if (o1.getCharacter() < o2.getCharacter()) {
          return -1;
        }
        return 0;
      }
      return 1;
    }
  }
}
