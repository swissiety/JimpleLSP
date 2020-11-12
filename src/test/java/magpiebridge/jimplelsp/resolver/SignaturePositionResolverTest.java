package magpiebridge.jimplelsp.resolver;

import de.upb.swt.soot.core.signatures.Signature;
import java.io.IOException;
import java.nio.file.Paths;
import junit.framework.TestCase;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class SignaturePositionResolverTest extends TestCase {

  private SignaturePositionResolver resolver;
  private SignaturePositionResolver fieldResolver;

  @Override
  protected void setUp() {
    try {
      resolver =
          new SignaturePositionResolver(
              Paths.get("src/test/resources/signatureOccurences.jimple").toAbsolutePath());
      fieldResolver =
          new SignaturePositionResolver(
              Paths.get("src/test/resources/FieldSignatureOccurences.jimple"));
    } catch (IOException exception) {
      exception.printStackTrace();
      fail("filenotfound");
    }
  }

  public void testResolveClassSigFromClassDefinition() {
    final Pair<Signature, Range> sig = resolver.resolve(new Position(2, 20));
    assertNotNull(sig);
    assertEquals("de.upb.Car", sig.getLeft().toString());
  }

  public void testResolveMethodSigFromMethodDefinitionExcatStart() {
    // beginning position
    final Pair<Signature, Range> sig = resolver.resolve(new Position(16, 17));
    assertNotNull(sig);
    assertEquals("<de.upb.Car: void driving()>", sig.getLeft().toString());
  }

  public void testResolveMethodSigFromMethodDefinition() {
    // middle position
    final Pair<Signature, Range> sig = resolver.resolve(new Position(16, 18));
    assertNotNull(sig);
    assertEquals("<de.upb.Car: void driving()>", sig.getLeft().toString());
  }

  public void testResolveClassSigUsageFromMethodSigException() {
    final Pair<Signature, Range> sig = resolver.resolve(new Position(16, 46));
    assertNotNull(sig);
    assertEquals("java.lang.Exception", sig.getLeft().toString());
  }

  public void testResolveClassSigUsageFromExtends() {
    final Pair<Signature, Range> sig = resolver.resolve(new Position(2, 42));
    assertNotNull(sig);
    assertEquals("de.upb.Vehicle", sig.getLeft().toString());
  }

  public void testResolveClassSigUsageFromImplements() {
    final Pair<Signature, Range> sig = resolver.resolve(new Position(2, 69));
    assertNotNull(sig);
    assertEquals("de.upb.SelfDriving", sig.getLeft().toString());
  }

  public void testResolveClassSigUsageFromBody() {
    {
      final Pair<Signature, Range> sig = resolver.resolve(new Position(25, 28));
      assertNotNull(sig);
      assertEquals("java.lang.Exception", sig.getLeft().toString());
    }

    {
      final Pair<Signature, Range> sig = resolver.resolve(new Position(23, 20));
      assertNotNull(sig);
      assertEquals("java.lang.Exception", sig.getLeft().toString());
    }
  }

  // methodsig iside body
  public void testResolveMethodSigUsageFromInsideBody() {
    final Pair<Signature, Range> sig = resolver.resolve(new Position(25, 57));
    assertNotNull(sig);
    assertEquals("<java.lang.Exception: void <init>(java.lang.String)>", sig.getLeft().toString());
  }

  public void testResolveClassSigUsageFromInsideBodyMethodParameterType() {
    final Pair<Signature, Range> sig = resolver.resolve(new Position(25, 62));
    assertNotNull(sig);
    assertEquals("java.lang.String", sig.getLeft().toString());
  }

  public void testResolveFieldSigUsageFromInsideBody() {
    final Pair<Signature, Range> sig = fieldResolver.resolve(new Position(17, 71));
    assertNotNull(sig);
    assertEquals(
        "<de.upb.soot.concrete.fieldReference.A: java.lang.String str>", sig.getLeft().toString());
  }
}
