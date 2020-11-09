package magpiebridge.jimplelsp.resolver;

import de.upb.swt.soot.core.signatures.Signature;
import junit.framework.TestCase;
import org.antlr.v4.runtime.CharStreams;
import org.eclipse.lsp4j.Position;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SignaturePositionResolverTest extends TestCase {

  SignaturePositionResolver resolver;

  @Override
  protected void setUp(){
    try {
      final Path path = Paths.get("src/test/resources/signatureOccurences.jimple").toAbsolutePath();
      resolver = new SignaturePositionResolver(path);
    } catch (IOException exception) {
      exception.printStackTrace();
      fail("filenotfound");
    }
  }

  public void testResolveClassSigFromClassDefinition() {
    // TODO
    final Signature sig = resolver.resolve(new Position(0, 0));
    assertNotNull(sig);
    assertEquals("", sig.toString());
  }

  public void testResolveMethodSigFromMethodDefinition()  {
    // TODO
    final Signature sig = resolver.resolve(new Position(0, 0));
    assertNotNull(sig);
    assertEquals("", sig.toString());
  }

  public void testResolvClassSigUsageFromMethodSigException() {
    // TODO
    final Signature sig = resolver.resolve(new Position(0, 0));
    assertNotNull(sig);
    assertEquals("", sig.toString());
  }

  public void testResolvClassSigUsageFromExtends() {
    // TODO
    final Signature sig = resolver.resolve(new Position(0, 0));
    assertNotNull(sig);
    assertEquals("", sig.toString());
  }

  public void testResolvClassSigUsageFromImplements(){
    // TODO
    final Signature sig = resolver.resolve(new Position(0, 0));
    assertNotNull(sig);
    assertEquals("", sig.toString());
  }

  public void testResolvClassSigUsageFromBody(){
    // TODO
    final Signature sig = resolver.resolve(new Position(0, 0));
    assertNotNull(sig);
    assertEquals("", sig.toString());
  }

  // methodsig iside body
  public void testResolvMethodSigUsageFromInsideBody(){
    // TODO
    final Signature sig = resolver.resolve(new Position(0, 0));
    assertNotNull(sig);
    assertEquals("", sig.toString());
  }
}