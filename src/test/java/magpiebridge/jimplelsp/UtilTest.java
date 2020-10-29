package magpiebridge.jimplelsp;

import junit.framework.TestCase;
import org.junit.Ignore;
import org.junit.Test;
import soot.Main;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

public class UtilTest {


  // TODO: test utf8 en/decoding in uri

  @Test
  public void testPathToUri() {

    assertEquals("file:/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Paths.get("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());
    assertEquals("file:/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Paths.get("file://home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());
    assertEquals("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1", Paths.get("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());

    assertEquals("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/", Util.pathToUri(Paths.get("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1")));
    // assertEquals("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/", Util.pathToUri(Paths.get("file:/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1")));
    // assertEquals("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/", Util.pathToUri(Paths.get("file://home/smarkus/IdeaProjects/JimpleLspExampleProject/module1")));
    // assertEquals("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/", Util.pathToUri(Paths.get("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1")));

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


  @Test
  @Ignore
  public void extractJimpleFromAPK(){
    // generate with old soot
    String rtJar =
            System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";

    String archivePath ="/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/com.uberspot.a2048_25.apk";
    String androidJarPath = " ";
    String[] options =
            new String[] {
                    "-cp",
                    archivePath + File.pathSeparator + rtJar,
                     "-android-jars", androidJarPath,
                    "-hierarchy-dirs",
                    "-f",
                    "jimple",
                    archivePath
            };
    Main.main(options);
  }


}