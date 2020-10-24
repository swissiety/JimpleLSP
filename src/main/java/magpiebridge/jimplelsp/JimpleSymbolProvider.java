package magpiebridge.jimplelsp;

import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.model.SootField;
import de.upb.swt.soot.core.model.SootMethod;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolKindCapabilities;

import javax.annotation.Nonnull;
import java.util.List;

public class JimpleSymbolProvider {

  static void retrieveAndFilterSymbolsFromClass(@Nonnull List<SymbolInformation> resultList, String query, @Nonnull SootClass clazz, @Nonnull SymbolKindCapabilities symbolKind) {
    final List<SymbolKind> clientSupportedSymbolKinds = symbolKind.getValueSet();

    if (clientSupportedSymbolKinds.contains(SymbolKind.Class)) {
      // retrieve classes
      if (clazz.getName().toLowerCase().startsWith(query)) {
        Location location = new Location(Util.classToUri(clazz), Util.positionToRange(clazz.getPosition()));
        resultList.add(new SymbolInformation(clazz.getName(), SymbolKind.Class, location));
      }
    }

    if (clientSupportedSymbolKinds.contains(SymbolKind.Method)) {
      // retrieve methods
      for (SootMethod method : clazz.getMethods()) {
        if (method.getName().toLowerCase().startsWith(query)) {
          Location location = new Location(Util.classToUri(clazz), Util.positionToRange(method.getPosition()));
          resultList.add(new SymbolInformation(method.getName(), SymbolKind.Method, location));
        }
      }
    }

    if (clientSupportedSymbolKinds.contains(SymbolKind.Field)) {
      // retrieve fields
      for (SootField field : clazz.getFields()) {
        if (field.getName().toLowerCase().startsWith(query)) {
          Location location = new Location(Util.classToUri(clazz), Util.positionToRange(field.getPosition()));
          resultList.add(new SymbolInformation(field.getName(), SymbolKind.Field, location));
        }
      }
    }
  }


}
