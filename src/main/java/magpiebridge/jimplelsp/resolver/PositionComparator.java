package magpiebridge.jimplelsp.resolver;

import org.eclipse.lsp4j.Position;

import java.util.Comparator;

class PositionComparator implements Comparator<Position> {
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