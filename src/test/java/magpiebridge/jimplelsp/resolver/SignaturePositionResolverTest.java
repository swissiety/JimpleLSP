package magpiebridge.jimplelsp.resolver;

import de.upb.swt.soot.core.signatures.Signature;
import junit.framework.TestCase;
import org.eclipse.lsp4j.Position;

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
    final Signature sig = resolver.resolve(new Position(2, 20));
    assertNotNull(sig);
    assertEquals("de.upb.Car", sig.toString());
  }

  public void testResolveMethodSigFromMethodDefinitionExcatStart()  {
    // beginning position
    final Signature sig = resolver.resolve(new Position(16, 17));
    assertNotNull(sig);
    assertEquals("<de.upb.Car: void driving()>", sig.toString());
  }

  public void testResolveMethodSigFromMethodDefinition()  {
    // middle position
    final Signature sig = resolver.resolve(new Position(16, 18));
    assertNotNull(sig);
    assertEquals("<de.upb.Car: void driving()>", sig.toString());
  }

  public void testResolveClassSigUsageFromMethodSigException() {
    final Signature sig = resolver.resolve(new Position(16, 46));
    assertNotNull(sig);
    assertEquals("java.lang.Exception", sig.toString());
  }

  public void testResolveClassSigUsageFromExtends() {
    final Signature sig = resolver.resolve(new Position(2, 42));
    assertNotNull(sig);
    assertEquals("de.upb.Vehicle", sig.toString());
  }

  public void testResolveClassSigUsageFromImplements(){
    final Signature sig = resolver.resolve(new Position(2, 69));
    assertNotNull(sig);
    assertEquals("de.upb.SelfDriving", sig.toString());
  }

  public void testResolveClassSigUsageFromBody(){
    {
      final Signature sig = resolver.resolve(new Position(25, 28));
      assertNotNull(sig);
      assertEquals("java.lang.Exception", sig.toString());
    }

    {
    final Signature sig = resolver.resolve(new Position(23, 20));
    assertNotNull(sig);
    assertEquals("java.lang.Exception", sig.toString());
    }
  }

  // methodsig iside body
  public void testResolveMethodSigUsageFromInsideBody(){
    final Signature sig = resolver.resolve(new Position(25, 57));
    assertNotNull(sig);
    assertEquals("<java.lang.Exception: void <init>(java.lang.String)>", sig.toString());
  }

  public void testResolveClassSigUsageFromInsideBodyMethodParameterType(){
    final Signature sig = resolver.resolve(new Position(25, 62));
    assertNotNull(sig);
    assertEquals("java.lang.String", sig.toString());
  }
}