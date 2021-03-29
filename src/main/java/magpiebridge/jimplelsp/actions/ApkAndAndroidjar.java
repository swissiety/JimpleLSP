package magpiebridge.jimplelsp.actions;

import org.apache.commons.io.IOUtils;
import org.jheaps.annotations.Beta;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.*;

/**
 * TODO
 *
 * @echo off
 * set sootPath=soot-4.2.1-jar-with-dependencies.jar
 * rem set target=E:\Git\Github\callgraph\TaintBench\allApks\fakedaum.apk
 * rem set output=E:\Git\Github\callgraph\CGBench_Test\fakedaum\sootoutput
 * set target=E:\Git\Github\callgraph\CGBench_Test\fakedaum\placeholder-lib.jar
 * set output=E:\Git\Github\callgraph\CGBench_Test\fakedaum\sootoutput
 * <p>
 * rem echo build soot-reloaded
 * rem set "cmdf2=mvn -f %sootRepo% com.coveo:fmt-maven-plugin:format"
 * rem call %cmdf2%
 * rem set "cmd1=mvn -f %sootRepo% install -DskipTests"
 * rem call %cmd1%
 * <p>
 * echo run soot
 * set "cmd=java -cp %sootPath% soot.Main -process-dir %target% -pp -src-prec c -allow-phantom-refs -d %output% -output-format J"
 * rem set "cmd=java -cp %sootPath% soot.Main -process-dir %target% -pp -src-prec apk -android-jars E:\Git\androidPlatforms -allow-phantom-refs -d %output% -output-format J"
 * call %cmd%
 * set target2=E:\Git\Github\callgraph\CGBench_Test\fakedaum\averroes-lib-class.jar
 * set "cmd2=java -cp %sootPath% soot.Main -process-dir %target2% -pp -src-prec c -allow-phantom-refs -d %output% -output-format J"
 * call %cmd2%
 * <p>
 * pause
 */
@Beta
class ApkAndAndroidjar {

  public static int extractApkVersion(String apkFile) {
    int[] apkVersion = new int[1];
    apkVersion[0] = -1;
    try (FileSystem fileSystem = FileSystems.newFileSystem(Paths.get(apkFile), null)) {
      Path fileToExtract = fileSystem.getPath("AndroidManifest.xml");

      // FIXME: this does not work for a compiled AndroidManifest...

      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser saxParser = factory.newSAXParser();
      InputStream input = fileSystem.provider().newInputStream(fileToExtract);

      System.out.println(IOUtils.toString(input, Charset.defaultCharset()));
      saxParser.parse(
              input,
              new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attr)
                        throws SAXException {
                  if (qName.equals("manifest")) {
                    String version = attr.getValue("android:versionCode");
                    if (version != null) {
                      apkVersion[0] = Integer.parseInt(version);
                      throw new SAXException(""); // abort parsing bc we have all we wanted
                    }
                  }
                }
              });

    } catch (SAXException e) {
      if (!e.getMessage().equals("")) {
        e.printStackTrace();
      }
    } catch (ParserConfigurationException | IOException e) {
      e.printStackTrace();
    }

    return apkVersion[0];
  }

  public static boolean downloadAndroidjar(int apkVersion, String targetPath) {

    // TODO: java -Xmx2g -jar sootclasses-trunk-jar-with-dependencies.jar soot.Main -w
    // -allow-phantom-refs -android-jars /home/smarkus/workspace/android-platforms/ -src-prec apk -f
    // jimple -process-dir
    // "/home/smarkus/IdeaProjects/JimpleLspExampleProject/module1/src/com.uberspot.a2048_25.apk"
    // FIXME: old soot wants a specific folder structure: parameter + / android-{version} /
    // android.jar

    final Path path = Paths.get(targetPath);
    try {
      Files.createDirectories(path.getParent());
    } catch (IOException e) {
      return false;
    }

    try (BufferedInputStream in =
                 new BufferedInputStream(
                         new URL(
                                 "https://github.com/Sable/android-platforms/blob/master/android-"
                                         + apkVersion
                                         + "/android.jar")
                                 .openStream());
         FileOutputStream fileOutputStream = new FileOutputStream(targetPath)) {
      byte[] dataBuffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
        fileOutputStream.write(dataBuffer, 0, bytesRead);
      }
    } catch (IOException e) {
      return false;
    }
    return true;
  }
}
