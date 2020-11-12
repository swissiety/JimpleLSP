package magpiebridge.jimplelsp.resolver;

import java.util.Comparator;
import org.eclipse.lsp4j.Position;

class PositionComparator implements Comparator<Position> {
  private static final PositionComparator INSTANCE = new PositionComparator();

  public static PositionComparator getInstance() {
    return INSTANCE;
  }

  @Override
  public int compare(Position o1, Position o2) {
    if (o1.getLine() < o2.getLine()) {
      return -1;
    } else if (o1.getLine() == o2.getLine()) {
      if (o1.getCharacter() < o2.getCharacter()) {
        return -1;
      } else if (o1.getCharacter() == o2.getCharacter()) {
        return 0;
      }
      return 1;
    }
    return 1;
  }
}
