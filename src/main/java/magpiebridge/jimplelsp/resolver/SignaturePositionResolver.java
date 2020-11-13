package magpiebridge.jimplelsp.resolver;

import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.jimple.Jimple;
import de.upb.swt.soot.core.model.Position;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.signatures.Signature;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.types.Type;
import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.JimpleParser;
import de.upb.swt.soot.jimple.parser.JimpleConverterUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import javax.annotation.Nullable;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Range;

/**
 * This Class holds information about (open) jimple files. Especially range information about
 * occurences of Signature's or even Siganture+Identifier.
 *
 * @author Markus Schmidt
 */
public class SignaturePositionResolver {
  private final SignatureOccurenceAggregator occurences = new SignatureOccurenceAggregator();
  private final Path fileUri;
  private final JimpleConverterUtil util;

  public SignaturePositionResolver(Path fileUri) throws IOException {
    this(fileUri, CharStreams.fromPath(fileUri));
  }

  public SignaturePositionResolver(Path fileUri, String content) {
    this(fileUri, CharStreams.fromString(content));
  }

  private SignaturePositionResolver(Path fileUri, CharStream charStream) {
    this.fileUri = fileUri;
    util = new JimpleConverterUtil(fileUri);
    JimpleParser parser = JimpleConverterUtil.createJimpleParser(charStream, fileUri);

    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(occurences, parser.file());
  }

  @Nullable
  public Pair<Signature, Range> resolve(org.eclipse.lsp4j.Position position) {
    return occurences.resolve(position);
  }

  private final class SignatureOccurenceAggregator extends JimpleBaseListener {
    SmartDatastructure positionContainer = new SmartDatastructure();
    ClassType clazz;

    @Override
    public void enterFile(JimpleParser.FileContext ctx) {
      if (ctx.classname == null) {
        throw new ResolveException(
            "Identifier for this unit is not found.",
            fileUri,
            JimpleConverterUtil.buildPositionFromCtx(ctx));
      }
      String classname = Jimple.unescape(ctx.classname.getText());
      clazz = util.getClassType(classname);

      final Position startpos = JimpleConverterUtil.buildPositionFromCtx(ctx.file_type());
      final Position endpos = JimpleConverterUtil.buildPositionFromCtx(ctx.classname);

      positionContainer.add( new Position(startpos.getFirstLine(),startpos.getFirstCol(),endpos.getLastLine(),endpos.getLastCol()), clazz);

      if (ctx.extends_clause() != null) {
        ClassType superclass = util.getClassType(ctx.extends_clause().classname.getText());
        positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx.extends_clause().classname), superclass);
      }

      super.enterFile(ctx);
    }

    @Override
    public void enterMethod(JimpleParser.MethodContext ctx) {
      // parsing the declaration
      Type type = util.getType(ctx.method_subsignature().type().getText());
      if (type == null) {
        throw new ResolveException(
            "Returntype not found.", fileUri, JimpleConverterUtil.buildPositionFromCtx(ctx));
      }
      String methodname = ctx.method_subsignature().method_name().getText();
      if (methodname == null) {
        throw new ResolveException(
            "Methodname not found.", fileUri, JimpleConverterUtil.buildPositionFromCtx(ctx));
      }

      List<Type> params = util.getTypeList(ctx.method_subsignature().type_list());
      MethodSignature methodSignature =
          util.getIdentifierFactory()
              .getMethodSignature(Jimple.unescape(methodname), clazz, type, params);
      positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx), methodSignature);

      super.enterMethod(ctx);
    }

    @Override
    public void enterMethod_signature(JimpleParser.Method_signatureContext ctx) {
      positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx.class_name), util.getClassType(ctx.class_name.getText()));
      final JimpleParser.Method_nameContext method_nameCtx =
          ctx.method_subsignature().method_name();
      positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(method_nameCtx), util.getMethodSignature(ctx, null));

      super.enterMethod_signature(ctx);
    }

    @Override
    public void enterField_signature(JimpleParser.Field_signatureContext ctx) {
      positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx.classname), util.getClassType(ctx.classname.getText()));
      positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx.fieldname), util.getFieldSignature(ctx));
      super.enterField_signature(ctx);
    }

    @Override
    public void enterConstant(JimpleParser.ConstantContext ctx) {
      if (ctx.CLASS() != null) {
        positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx.identifier()), util.getClassType(ctx.identifier().getText()));
      }
      super.enterConstant(ctx);
    }

    @Override
    public void enterValue(JimpleParser.ValueContext ctx) {
      if (ctx.NEW() != null) {
        positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx.base_type), util.getClassType(ctx.base_type.getText()));
      } else if (ctx.NEWARRAY() != null) {
        positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx.array_type), util.getClassType(ctx.array_type.getText()));
      } else if (ctx.NEWMULTIARRAY() != null) {
        positionContainer.add(
                JimpleConverterUtil.buildPositionFromCtx(ctx.multiarray_type), util.getClassType(ctx.multiarray_type.getText()));
      } else if (ctx.INSTANCEOF() != null) {
        positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx.nonvoid_type), util.getClassType(ctx.nonvoid_type.getText()));
      }
      super.enterValue(ctx);
    }

    @Override
    public void enterImportItem(JimpleParser.ImportItemContext ctx) {
      // add information for resolving classes correctly
      util.addImport(ctx);

      positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx.location), util.getClassType(ctx.location.getText()));
      super.enterImportItem(ctx);
    }

    @Override
    public void enterType(JimpleParser.TypeContext ctx) {
      final Type returnType = util.getType(ctx.getText());
      if (returnType instanceof ClassType) {
        positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx.identifier()), (Signature) returnType);
      }
      super.enterType(ctx);
    }

    @Nullable
    public Pair<Signature, Range> resolve(org.eclipse.lsp4j.Position position) {
      return positionContainer.resolve(position);
    }
  }
}
