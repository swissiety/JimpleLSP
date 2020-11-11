package magpiebridge.jimplelsp.resolver;

import de.upb.swt.soot.core.signatures.Signature;
import de.upb.swt.soot.jimple.parser.JimpleConverterUtil;
import org.antlr.v4.runtime.ParserRuleContext;
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
  @Nonnull List<Signature> signaturesAndIdentifiers = new ArrayList<>();

  Comparator<Position> comparator = new PositionComparator();

  void add(ParserRuleContext token, Signature sig) {
    // insert sorted to be accessed via binary search
    // lsp is zero indexed; antlrs line not
    final Position startPos = new Position(token.start.getLine()-1, token.start.getCharPositionInLine());
    int idx = Collections.binarySearch(startPositions, startPos, new PositionComparator());
    if(idx < 0){
      // calculate insertion index
      idx = -idx-1;
    }else{
      throw new IllegalStateException("position "+startPos+" is already taken.");
    }

    startPositions.add(idx, startPos);
    final de.upb.swt.soot.core.model.Position position = JimpleConverterUtil.buildPositionFromCtx(token);
    endPositions.add(idx, new Position(position.getLastLine(), position.getLastCol()));

    signaturesAndIdentifiers.add(idx, sig);
  }

  @Nullable
  Signature resolve(Position position) {
    if(startPositions.isEmpty()){
      return null;
    }
    int index = Collections.binarySearch(startPositions, position, PositionComparator.getInstance());
    if (index < 0) {
      // not exactly found: check if next smaller neighbour is surrounding it
      index = (-index)-1-1;
    } else if (index >= startPositions.size()) {
      // not exactly found: (greater than last element) check if next smaller neighbour is surrounding it
      index = index-1;
    }

    if(index < 0 ){
      return null;
    }

    if (comparator.compare(startPositions.get(index), position) <= 0
        && comparator.compare(position, endPositions.get(index)) <= 0) {
      return signaturesAndIdentifiers.get(index);
    }
    return null;
  }
}
