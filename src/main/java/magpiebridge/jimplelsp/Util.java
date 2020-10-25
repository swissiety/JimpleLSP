package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.model.Position;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.java.core.JavaIdentifierFactory;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class Util {

  @Nonnull
  public static String classToUri(@Nonnull SootClass clazz) {
    return pathToUri(clazz.getClassSource().getSourcePath());
  }

  @Nonnull
  public static String pathToUri(@Nonnull Path sourcePath) {
    return "file://" + sourcePath.toString();
  }

  @Nonnull
  public static ClassType uritoClasstype(@Nonnull String strUri) {
    final String baseName = FilenameUtils.getBaseName(strUri);
    return JavaIdentifierFactory.getInstance().getClassType(baseName);
  }

  @Nonnull
  public static Range positionToRange(@Nonnull Position position) {
    return new Range(
        new org.eclipse.lsp4j.Position(position.getFirstLine(), position.getFirstCol()),
        new org.eclipse.lsp4j.Position(position.getLastLine(), position.getLastCol()));
  }

  public static Either<List<? extends Location>, List<? extends LocationLink>> positionToLocation(
      @Nonnull String uri, @Nonnull Position position) {
    return Either.forLeft(Collections.singletonList(new Location(uri, positionToRange(position))));
  }
}
