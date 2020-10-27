package magpiebridge.jimplelsp;

import junit.framework.TestCase;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotEquals;

public class UtilTest extends TestCase {


  // TODO: test utf8 en/decoding in uri

  @Test
  public void testPathToUri() {

    assertEquals("file:/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Paths.get("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());
    assertEquals("file:/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Paths.get("file://home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());
    assertEquals("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Paths.get("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());

    assertNotEquals("file://home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Util.pathToUri(Paths.get("file://home/smarkus/IdeaProjects/JimpleLspExampleProject/module1")));
    assertNotEquals("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Util.pathToUri(Paths.get("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1")));
    assertNotEquals("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Util.pathToUri(Paths.get("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1")));

  }

  @Test
  public void testUriToPath() {
    assertEquals("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Util.uriToPath("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());
    try {
      assertEquals("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Util.uriToPath("file://home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());
    fail();
    }catch (IllegalArgumentException ignore){};

    assertEquals("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Util.uriToPath("file:/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());

    try {
      assertEquals("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Util.uriToPath("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());
    //  fail();
    }catch (IllegalArgumentException ignore){};
  }
}