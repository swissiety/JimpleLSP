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
    final Signature sig = resolver.resolve(new Position(3, 20));
    assertNotNull(sig);
    assertEquals("de.upb.Car", sig.toString());
  }

  public void testResolveMethodSigFromMethodDefinition()  {
    // beginning position
    {
      final Signature sig = resolver.resolve(new Position(17, 17));
      assertNotNull(sig);
      assertEquals("driving", sig.toString());
    }

    // middle position
    {
      final Signature sig = resolver.resolve(new Position(17, 18));
      assertNotNull(sig);
      assertEquals("driving", sig.toString());
    }
  }

  public void testResolveClassSigUsageFromMethodSigException() {
    final Signature sig = resolver.resolve(new Position(0, 0));
    assertNotNull(sig);
    assertEquals("java.lang.Exception", sig.toString());
  }

  public void testResolveClassSigUsageFromExtends() {
    final Signature sig = resolver.resolve(new Position(3, 42));
    assertNotNull(sig);
    assertEquals("de.upb.Vehicle", sig.toString());
  }

  public void testResolveClassSigUsageFromImplements(){
    final Signature sig = resolver.resolve(new Position(3, 69));
    assertNotNull(sig);
    assertEquals("de.upb.SelfDriving", sig.toString());
  }

  public void testResolveClassSigUsageFromBody(){
    final Signature sig = resolver.resolve(new Position(24, 24));
    assertNotNull(sig);
    assertEquals("java.lang.Exception", sig.toString());
  }

  // methodsig iside body
  public void testResolveMethodSigUsageFromInsideBody(){
    final Signature sig = resolver.resolve(new Position(26, 28));
    assertNotNull(sig);
    assertEquals("java.lang.Exception: void <init>(java.lang.String)", sig.toString());
  }
}