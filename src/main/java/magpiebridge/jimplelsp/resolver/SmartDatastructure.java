package magpiebridge.jimplelsp.resolver;

import de.upb.swt.soot.core.signatures.Signature;
import org.antlr.v4.runtime.Token;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Position;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;



// TODO: improve ds: implement or use dependency for sth like segment/intervaltree
class SmartDatastructure {

  @Nonnull
  List<Position> startPositions = new ArrayList<>();
  @Nonnull List<Position> endPositions = new ArrayList<>();
  @Nonnull List<Pair<Signature, String>> signaturesAndIdentifiers = new ArrayList<>();

  Comparator<Position> comparator = new PositionComparator();

  void add(Token start, Token end, Signature sig, @Nullable String identifier) {
    final Position startPos = new Position(start.getLine(), start.getCharPositionInLine());
    int idx = Collections.binarySearch(startPositions, startPos, new PositionComparator());
    if(idx < 0){
      idx = 0;
    }
    startPositions.add(idx, startPos);
    endPositions.add(idx, new Position(end.getLine(), end.getCharPositionInLine()));
    signaturesAndIdentifiers.add(idx, Pair.of(sig, identifier));
  }

  @Nullable
  Pair<Signature, String> resolve(Position position) {
    int index = Collections.binarySearch(startPositions, position, new PositionComparator());

    if (index < 0 || index >= startPositions.size()) {
      // not exactly found;
      // index = -index + 1;
      return null;
    }
    // FIXME: BUG
    if (comparator.compare(startPositions.get(index), position) <= 0
        && comparator.compare(position, endPositions.get(index)) <= 0) {
      return signaturesAndIdentifiers.get(index);
    }
    return null;
  }
}
