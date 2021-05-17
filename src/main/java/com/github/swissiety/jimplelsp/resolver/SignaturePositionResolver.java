package com.github.swissiety.jimplelsp.resolver;

import com.github.swissiety.jimplelsp.Util;
import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.jimple.Jimple;
import de.upb.swt.soot.core.model.Position;
import de.upb.swt.soot.core.signatures.FieldSignature;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.signatures.Signature;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.types.Type;
import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.JimpleParser;
import de.upb.swt.soot.jimple.parser.JimpleConverterUtil;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This Class organizes information about (open) jimple files. Especially range information about
 * occurences of Signature's in the given file.
 *
 * @author Markus Schmidt
 */
public class SignaturePositionResolver {
  @Nonnull private final SignatureOccurenceAggregator occurences = new SignatureOccurenceAggregator();
  @Nonnull private final Path path;
  @Nonnull private final JimpleConverterUtil util;

  public SignaturePositionResolver(@Nonnull Path path, @Nonnull ParseTree parseTree) {
    this.path = path;
    util = new JimpleConverterUtil(path);

    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(occurences, parseTree);
  }

  @Nullable
  public Pair<Signature, Range> resolve(@Nonnull org.eclipse.lsp4j.Position position) {
    return occurences.resolve(position);
  }

  @Nullable
  public List<Location> resolve(@Nonnull Signature signature) {
    return occurences.resolve(signature).stream()
        .map(range -> new Location(Util.pathToUri(path), range))
        .collect(Collectors.toList());
  }

  /** skips e.g. the methods returntype to get the identifier (or class type) */
  @Nullable
  public Location findFirstMatchingSignature(Signature signature, Position position) {
    final Range firstMatchingSignature = occurences.findFirstMatchingSignature(signature, position);
    if (firstMatchingSignature == null) {
      return null;
    }
    return new Location(Util.pathToUri(path), firstMatchingSignature);
  }

  private final class SignatureOccurenceAggregator extends JimpleBaseListener {

    SignatureRangeContainer positionContainer = new SignatureRangeContainer();
    ClassType clazz;

    @Nullable
    public Pair<Signature, Range> resolve(org.eclipse.lsp4j.Position position) {
      return positionContainer.resolve(position);
    }

    public List<Range> resolve(Signature signature) {
      return positionContainer.resolve(signature);
    }

    public Range findFirstMatchingSignature(Signature signature, Position position) {
      return positionContainer.findFirstMatchingSignature(signature, position);
    }

    @Override
    public void enterFile(JimpleParser.FileContext ctx) {
      if (ctx.classname == null) {
        throw new ResolveException(
            "Identifier for this unit is not found.",
            path,
            JimpleConverterUtil.buildPositionFromCtx(ctx));
      }
      String classname = Jimple.unescape(ctx.classname.getText());
      clazz = util.getClassType(classname);

      positionContainer.add(JimpleConverterUtil.buildPositionFromCtx(ctx.classname), clazz);

      if (ctx.extends_clause() != null) {
        ClassType superclass = util.getClassType(ctx.extends_clause().classname.getText());
        positionContainer.add(
            JimpleConverterUtil.buildPositionFromCtx(ctx.extends_clause().classname), superclass);
      }

      super.enterFile(ctx);
    }

    @Override
    public void enterMethod(JimpleParser.MethodContext ctx) {
      // parsing the declaration
      Type type = util.getType(ctx.method_subsignature().type().getText());
      if (type == null) {
        throw new ResolveException(
            "Returntype not found.", path, JimpleConverterUtil.buildPositionFromCtx(ctx));
      }
      String methodname = ctx.method_subsignature().method_name().getText();
      if (methodname == null) {
        throw new ResolveException(
            "Methodname not found.", path, JimpleConverterUtil.buildPositionFromCtx(ctx));
      }

      List<Type> params = util.getTypeList(ctx.method_subsignature().type_list());
      MethodSignature methodSignature =
          util.getIdentifierFactory()
              .getMethodSignature(Jimple.unescape(methodname), clazz, type, params);

      positionContainer.add(
          JimpleConverterUtil.buildPositionFromCtx(ctx.method_subsignature().method_name()),
          methodSignature);

      super.enterMethod(ctx);
    }

    @Override
    public void enterField(JimpleParser.FieldContext ctx) {
      String fieldname = ctx.identifier().getText();
      FieldSignature fieldSignature =
          util.getIdentifierFactory()
              .getFieldSignature(
                  Jimple.unescape(fieldname), clazz, util.getType(ctx.type().getText()));
      positionContainer.add(
          JimpleConverterUtil.buildPositionFromCtx(ctx.identifier()), fieldSignature);
      super.enterField(ctx);
    }

    @Override
    public void enterMethod_signature(JimpleParser.Method_signatureContext ctx) {
      positionContainer.add(
          JimpleConverterUtil.buildPositionFromCtx(ctx.class_name),
          util.getClassType(ctx.class_name.getText()));
      final JimpleParser.Method_nameContext method_nameCtx =
          ctx.method_subsignature().method_name();
      positionContainer.add(
          JimpleConverterUtil.buildPositionFromCtx(method_nameCtx),
          util.getMethodSignature(ctx, null));

      super.enterMethod_signature(ctx);
    }

    @Override
    public void enterField_signature(JimpleParser.Field_signatureContext ctx) {
      positionContainer.add(
          JimpleConverterUtil.buildPositionFromCtx(ctx.classname),
          util.getClassType(ctx.classname.getText()));
      positionContainer.add(
          JimpleConverterUtil.buildPositionFromCtx(ctx.fieldname), util.getFieldSignature(ctx));
      super.enterField_signature(ctx);
    }

    @Override
    public void enterConstant(JimpleParser.ConstantContext ctx) {
      if (ctx.CLASS() != null) {
        positionContainer.add(
            JimpleConverterUtil.buildPositionFromCtx(ctx.identifier()),
            util.getClassType(ctx.identifier().getText()));
      }
      super.enterConstant(ctx);
    }

    @Override
    public void enterValue(JimpleParser.ValueContext ctx) {
      if (ctx.NEW() != null) {
        positionContainer.add(
            JimpleConverterUtil.buildPositionFromCtx(ctx.base_type),
            util.getClassType(ctx.base_type.getText()));
      }
      super.enterValue(ctx);
    }

    @Override
    public void enterImportItem(JimpleParser.ImportItemContext ctx) {
      // add information for resolving classes correctly
      util.addImport(ctx);

      positionContainer.add(
          JimpleConverterUtil.buildPositionFromCtx(ctx.location),
          util.getClassType(ctx.location.getText()));
      super.enterImportItem(ctx);
    }

    @Override
    public void enterType(JimpleParser.TypeContext ctx) {
      final Type type = util.getType(ctx.getText());
      if (type instanceof ClassType) {
        positionContainer.add(
            JimpleConverterUtil.buildPositionFromCtx(ctx.identifier()), (Signature) type);
      }
      super.enterType(ctx);
    }
  }
}
