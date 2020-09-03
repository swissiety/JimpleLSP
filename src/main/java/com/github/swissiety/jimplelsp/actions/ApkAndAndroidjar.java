package com.github.swissiety.jimplelsp.actions;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApkAndAndroidjar {

  static int extractApkVersion(String apkFile) {
    int[] apkVersion = new int[1];
    apkVersion[0] = -1;
    try (FileSystem fileSystem = FileSystems.newFileSystem(Paths.get(apkFile), null)) {
      Path fileToExtract = fileSystem.getPath("AndroidManifest.xml");

      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser saxParser = factory.newSAXParser();
      saxParser.parse(fileToExtract.toFile(), new DefaultHandler() {
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attr) throws SAXException {
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
    try (BufferedInputStream in = new BufferedInputStream(new URL("https://github.com/Sable/android-platforms/blob/master/android-" + apkVersion + "/android.jar").openStream());
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
