package magpiebridge.jimplelsp;

import com.ibm.wala.classLoader.Module;
import java.util.Collection;
import magpiebridge.core.*;

/** @author Linghui Luo */
public class JimpleServerAnalysis implements ServerAnalysis {

  @Override
  public String source() {
    return "JimpleLsp";
  }

  @Override
  public void analyze(Collection<? extends Module> files, AnalysisConsumer server, boolean rerun) {
    // TODO: implement functionality for SUS
  }
}
