package magpiebridge.jimplelsp.resolver;

import de.upb.swt.soot.core.frontend.ResolveException;
import de.upb.swt.soot.core.jimple.Jimple;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.signatures.Signature;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.core.types.Type;
import de.upb.swt.soot.jimple.JimpleBaseListener;
import de.upb.swt.soot.jimple.parser.JimpleConverterUtil;
import de.upb.swt.soot.jimple.JimpleParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import javax.annotation.Nullable;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.commons.lang3.tuple.Pair;

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
    this (fileUri, CharStreams.fromPath(fileUri));
  }

  public SignaturePositionResolver(Path fileUri, String content) {
    this(fileUri, CharStreams.fromString(content));
  }

  private SignaturePositionResolver(Path fileUri, CharStream charStream) {
    this.fileUri = fileUri;
    util = new JimpleConverterUtil(fileUri);
    JimpleParser parser = JimpleConverterUtil.createJimpleParser(charStream, fileUri);

    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(occurences, parser.file() );
  }

  @Nullable
  public Signature resolve(org.eclipse.lsp4j.Position position) {
    return occurences.resolve(position);
  }

  private final class SignatureOccurenceAggregator extends JimpleBaseListener {
    SmartDatastructure positionContainer = new SmartDatastructure();
    ClassType clazz;

    private void addType(JimpleParser.TypeContext typeCtx) {
      final Type returnType = util.getType(typeCtx.getText());
      if(returnType instanceof ClassType){
        positionContainer.add(typeCtx.identifier(), (Signature) returnType,null );
      }
    }

    @Override
    public void enterFile(JimpleParser.FileContext ctx  ) {
      if (ctx.classname == null) {
        throw new ResolveException(
            "Identifier for this unit is not found.", fileUri, JimpleConverterUtil.buildPositionFromCtx(ctx));
      }
      String classname = Jimple.unescape(ctx.classname.getText());
      clazz = util.getClassType(classname);
      positionContainer.add(ctx.classname, clazz, null);

      if (ctx.extends_clause() != null) {
        ClassType superclass = util.getClassType(ctx.extends_clause().classname.getText());
        positionContainer.add(ctx.extends_clause().classname, superclass, null);
      }

      if (ctx.implements_clause() != null) {
        List<ClassType> interfaces = util.getClassTypeList(ctx.implements_clause().type_list());
        for (int i = 0, interfacesSize = interfaces.size(); i < interfacesSize; i++) {
          ClassType anInterface = interfaces.get(i);
          final JimpleParser.TypeContext interfaceToken =
              ctx.implements_clause().type_list().type(i);
          positionContainer.add(interfaceToken, anInterface, null);
        }
      }

      super.enterFile(ctx);
    }

    @Override
    public void enterDeclaration(JimpleParser.DeclarationContext ctx) {
      JimpleParser.TypeContext typeCtx = ctx.type();
      addType(typeCtx);
      super.enterDeclaration(ctx);
    }

    @Override
    public void enterMethod(JimpleParser.MethodContext ctx) {
      // parsing the declaration

      Type type = util.getType(ctx.type().getText());
      if (type == null) {
        throw new ResolveException(
            "Returntype not found.", fileUri, JimpleConverterUtil.buildPositionFromCtx(ctx));
      }
      String methodname = ctx.method_name().getText();
      if (methodname == null) {
        throw new ResolveException(
            "Methodname not found.", fileUri, JimpleConverterUtil.buildPositionFromCtx(ctx));
      }

      List<Type> params = util.getTypeList(ctx.type_list());
      MethodSignature methodSignature =
          util.getIdentifierFactory()
              .getMethodSignature(
                  Jimple.unescape(methodname), clazz, type, params);
      positionContainer.add(ctx, methodSignature, null);

      if (ctx.throws_clause() != null) {
        List<ClassType> exceptions = util.getClassTypeList(ctx.throws_clause().type_list());
        for (int i = 0, exceptionsSize = exceptions.size(); i < exceptionsSize; i++) {
          final JimpleParser.TypeContext typeContext = ctx.throws_clause().type_list().type(i);
          positionContainer.add(typeContext, exceptions.get(i), null);
        }
      }

      super.enterMethod(ctx);
    }

    @Override
    public void enterMethod_signature(JimpleParser.Method_signatureContext ctx) {
      positionContainer.add(ctx.class_name, util.getClassType(ctx.class_name.getText()), null);
      final JimpleParser.Method_nameContext method_nameCtx = ctx.method_subsignature().method_name();
      positionContainer.add(method_nameCtx, util.getMethodSignature(ctx, null), null);
      // returntype
      final JimpleParser.TypeContext returntypeCtx = ctx.method_subsignature().type();
      addType(returntypeCtx);

      // parameter types
      final JimpleParser.Type_listContext type_listContext = ctx.method_subsignature().type_list();
      if( type_listContext!= null) {
        for (JimpleParser.TypeContext typeCtx : type_listContext.type()) {
          final Type paramType = util.getType(typeCtx.identifier().getText());
          if(paramType instanceof ClassType) {
            positionContainer.add(typeCtx.identifier(), (Signature) paramType, null);
          }
        }
      }

      super.enterMethod_signature(ctx);
    }

    @Override
    public void enterField_signature(JimpleParser.Field_signatureContext ctx) {
      positionContainer.add(ctx.classname, util.getClassType(ctx.classname.getText()), null);
      positionContainer.add(ctx.fieldname, util.getFieldSignature(ctx), null);
      super.enterField_signature(ctx);
    }

    @Override
    public void enterIdentity_ref(JimpleParser.Identity_refContext ctx) {
      if (ctx.type() != null) {
        final Type type = util.getType(ctx.type().getText());
        if(type instanceof ClassType) {
          positionContainer.add(ctx.type(), (Signature) type, null);
        }
      }
      super.enterIdentity_ref(ctx);
    }

    @Override
    public void enterConstant(JimpleParser.ConstantContext ctx) {
      if( ctx.CLASS() != null){
        positionContainer.add(ctx.identifier(), util.getClassType(ctx.identifier().getText()), null);
      }
      super.enterConstant(ctx);
    }

    @Override
    public void enterImportItem(JimpleParser.ImportItemContext ctx) {
      // add information for resolving classes correctly
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

}
