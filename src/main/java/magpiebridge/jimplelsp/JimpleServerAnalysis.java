package magpiebridge.jimplelsp;

import com.ibm.wala.classLoader.Module;
import java.util.Collection;
import magpiebridge.core.*;

/** @author Markus Schmidt */
public class JimpleServerAnalysis implements ServerAnalysis {

  @Override
  public String source() {
    return "[Your Tool] + JimpleLSP";
  }

  @Override
  public void analyze(Collection<? extends Module> files, AnalysisConsumer server, boolean rerun) {
    // use this as the entrypoint for Jimple analyses
  }
}
