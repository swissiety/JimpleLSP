package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.model.Position;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.java.core.JavaIdentifierFactory;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import java.util.HashMap;
import java.util.Map;

public class Util {

  public static String classToUri(SootClass clazz) {
    return "file://" + clazz.getClassSource().getSourcePath();
  }

  public static ClassType uritoClasstype(String strUri) {
    final String baseName = FilenameUtils.getBaseName(strUri);
    return JavaIdentifierFactory.getInstance().getClassType(baseName);
  }

  public static Range positionToRange(Position position) {
    return new Range(new org.eclipse.lsp4j.Position(position.getFirstLine(), position.getFirstCol()), new org.eclipse.lsp4j.Position(position.getLastLine(), position.getLastCol()));
  }
}
