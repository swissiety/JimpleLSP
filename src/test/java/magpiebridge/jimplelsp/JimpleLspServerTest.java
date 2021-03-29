package magpiebridge.jimplelsp;

import junit.framework.TestCase;
import org.eclipse.lsp4j.*;

import java.util.ArrayList;
import java.util.List;

public class JimpleLspServerTest extends TestCase {

  public void testSth() {
  }

  // TODO: remove
  private List<Diagnostic> validate(DidOpenTextDocumentParams model) {

    List<Diagnostic> res = new ArrayList<>();

    Diagnostic diagnostic = new Diagnostic();
    diagnostic.setSeverity(DiagnosticSeverity.Error);
    diagnostic.setMessage("The .jimple file contains an Error");
    diagnostic.setSource("JimpleParser");
    int line = 5;
    int charOffset = 4;
    int length = 10;
    diagnostic.setRange(
            new Range(new Position(line, charOffset), new Position(line, charOffset + length)));

    final ArrayList<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();
    relatedInformation.add(
            new DiagnosticRelatedInformation(
                    new Location(
                            model.getTextDocument().getUri(),
                            new Range(new Position(1, 5), new Position(2, 0))),
                    "here is sth strange"));

    boolean uriOne = model.getTextDocument().getUri().endsWith("helloworld.jimple");
    String uriA =
            "file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/"
                    + (uriOne ? "another" : "helloworld")
                    + ".jimple";
    String uriB =
            "file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/"
                    + (!uriOne ? "another" : "helloworld")
                    + ".jimple";

    relatedInformation.add(
            new DiagnosticRelatedInformation(
                    new Location(uriA, new Range(new Position(2, 0), new Position(3, 0))),
                    "more strangeness"));
    diagnostic.setRelatedInformation(relatedInformation);

    res.add(diagnostic);

    Diagnostic diagnostic2 = new Diagnostic();
    diagnostic2.setMessage("This is a Jimple Error. ");
    diagnostic2.setRange(new Range(new Position(2, 0), new Position(2, 10)));
    diagnostic2.setSource("Jimpleparser");
    diagnostic2.setCode("403 Forbidden");
    diagnostic2.setSeverity(DiagnosticSeverity.Error);

    List<DiagnosticRelatedInformation> related = new ArrayList<>();
    related.add(
            new DiagnosticRelatedInformation(
                    new Location(uriA, new Range(new Position(4, 4), new Position(4, 10))), "problem A"));
    related.add(
            new DiagnosticRelatedInformation(
                    new Location(uriB, new Range(new Position(6, 6), new Position(6, 12))),
                    "another problem: B"));
    related.add(
            new DiagnosticRelatedInformation(
                    new Location(uriA, new Range(new Position(8, 8), new Position(8, 14))), "problem C"));
    diagnostic2.setRelatedInformation(related);

    res.add(diagnostic2);
    return res;
  }
}
