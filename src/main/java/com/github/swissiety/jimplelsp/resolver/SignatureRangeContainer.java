package com.github.swissiety.jimplelsp.resolver;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import sootup.core.signatures.Signature;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The Datastructure holds information about Signatures of Soot and their Range positions in a File.
 *
 * @author Markus Schmidt
 */
class SignatureRangeContainer {

  @Nonnull List<Position> startPositions = new ArrayList<>();
  @Nonnull List<Position> endPositions = new ArrayList<>();
  @Nonnull List<Signature> signatures = new ArrayList<>();

  Comparator<Position> comparator = new PositionComparator();

  void add( sootup.core.model.Position position, Signature sig) {
    // insert sorted to be accessed via binary search
    final Position startPos = new Position(position.getFirstLine(), position.getFirstCol());
    int idx = Collections.binarySearch(startPositions, startPos, new PositionComparator());
    if (idx < 0) {
      // calculate insertion index
      idx = -idx - 1;

    } else {
      throw new IllegalStateException(
          "position " + startPos + " is already taken by " + signatures.get(idx));
    }

    startPositions.add(idx, startPos);
    endPositions.add(idx, new Position(position.getLastLine(), position.getLastCol()));

    signatures.add(idx, sig);
  }

  @Nullable
  Pair<Signature, Range> resolve(@Nonnull Position position) {
    if (startPositions.isEmpty()) {
      return null;
    }
    int index = getStartingIndex(position);

    if (index < 0) {
      return null;
    }

    final Position startPos = startPositions.get(index);
    final Position endPos = endPositions.get(index);
    if (comparator.compare(startPos, position) <= 0 && comparator.compare(position, endPos) <= 0) {
      return Pair.of(signatures.get(index), new Range(startPos, endPos));
    }
    return null;
  }

  // binary search for position or return next smaller occurence (i.e. start position of a Range)
  private int getStartingIndex(@Nonnull Position position) {
    int index =
        Collections.binarySearch(startPositions, position, PositionComparator.getInstance());
    if (index < 0) {
      // not exactly found: check if next smaller neighbour is surrounding it
      index = (-index) - 1 - 1;
    } else if (index >= startPositions.size()) {
      // not exactly found: (greater than last element) check if next smaller neighbour is
      // surrounding it
      index = index - 1;
    }
    return Math.max(0, index);
  }

  public List<Range> resolve(@Nonnull Signature signature) {
    final List<Range> ranges = new ArrayList<>();
    for (int i = 0, signaturesSize = signatures.size(); i < signaturesSize; i++) {
      Signature sig = signatures.get(i);
      if (sig.equals(signature)) {
        ranges.add(new Range(startPositions.get(i), endPositions.get(i)));
      }
    }
    return ranges;
  }

  @Nullable
  public Range findFirstMatchingSignature(
      Signature signature, sootup.core.model.Position position) {

    int idx = getStartingIndex(new Position(position.getFirstLine(), position.getFirstCol()));

    // loop is expected to do max. 2 iterations
    for (int i = idx, signaturesSize = signatures.size(); i < signaturesSize; i++) {
      Signature sig = signatures.get(i);
      if (sig.equals(signature)) {
        return new Range(startPositions.get(i), endPositions.get(i));
      }
    }
    return null;
  }
}
