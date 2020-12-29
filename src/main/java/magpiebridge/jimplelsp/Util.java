package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.model.Position;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.jimple.parser.JimpleConverterUtil;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/** @author Markus Schmidt */
public class Util {

  @Nonnull
  public static String classToUri(@Nonnull SootClass clazz) {
    return pathToUri(clazz.getClassSource().getSourcePath());
  }

  @Nonnull
  public static String pathToUri(@Nonnull Path sourcePath) {
    return sourcePath.toUri().toString();
  }

  @Nonnull
  public static Path uriToPath(@Nonnull String uri) {
    return Paths.get(URI.create(uri));
  }

  @Nonnull
  public static Range ctxToRange(@Nonnull ParserRuleContext ctx) {
    // line numbers starting zero-based in LSP vs one-based in antlr
    final Position sootPosition = JimpleConverterUtil.buildPositionFromCtx(ctx);
    return new Range(
        new org.eclipse.lsp4j.Position(sootPosition.getFirstLine(), sootPosition.getFirstCol()),
        new org.eclipse.lsp4j.Position(sootPosition.getLastLine(), sootPosition.getLastCol()));
  }

  @Nonnull
  public static Range positionToDefRange(@Nonnull Position position) {
    // line numbers starting zero-based in LSP vs one-based in antlr
    // extract interesting part /beginning which usually is the signature of the current Range
    return new Range(
            new org.eclipse.lsp4j.Position(position.getFirstLine(), position.getFirstCol()),
            // to next line
            new org.eclipse.lsp4j.Position(position.getFirstLine() + 1, 0));
  }

  @Nonnull
  public static Range positionToRange(@Nonnull Position position) {
    // line numbers starting zero-based in LSP vs one-based in antlr
    return new Range(
            new org.eclipse.lsp4j.Position(position.getFirstLine(), position.getFirstCol()),
            new org.eclipse.lsp4j.Position(position.getLastLine(), position.getLastCol()));
  }

  public static Either<List<? extends Location>, List<? extends LocationLink>>
      positionToLocationList(@Nonnull String uri, @Nonnull Position position) {
    return Either.forLeft(Collections.singletonList(new Location(uri, positionToDefRange(position))));
  }

  public static Location positionToDefLocation(@Nonnull String uri, @Nonnull Position position) {
    return new Location(uri, positionToDefRange(position));
  }

  public static Location positionToLocation(@Nonnull String uri, @Nonnull Position position) {
    return new Location(uri, positionToRange(position));
  }

}
