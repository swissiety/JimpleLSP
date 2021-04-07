package com.github.swissiety.jimplelsp;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.fail;

import java.nio.file.Paths;
import org.junit.Test;

public class UtilTest {

  // TODO: test utf8 en/decoding in uri

  @Test
  public void testPathToUri() {

    assertEquals(
        "file:/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1",
        Paths.get("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());
    assertEquals(
        "file:/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1",
        Paths.get("file://home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());
    assertEquals(
        "/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1",
        Paths.get("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());

    assertEquals(
        "file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/",
        Util.pathToUri(Paths.get("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1")));
  }

  @Test
  public void testUriToPath() {
    assertEquals(
        "/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1",
        Util.uriToPath("file:///home/smarkus/IdeaProjects/JimpleLspExampleProject/module1")
            .toString());
    try {
      assertEquals(
          "/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1",
          Util.uriToPath("file://home/smarkus/IdeaProjects/JimpleLspExampleProject/module1")
              .toString());
      fail();
    } catch (IllegalArgumentException ignore) {
    }
    ;

    assertEquals(
        "/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1",
        Util.uriToPath("file:/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1")
            .toString());

    try {
      assertEquals(
          "/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1",
          Util.uriToPath("/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1").toString());
      //  fail();
    } catch (IllegalArgumentException ignore) {
    }
    ;
  }
}
