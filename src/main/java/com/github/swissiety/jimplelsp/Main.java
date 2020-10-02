package com.github.swissiety.jimplelsp;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import javax.annotation.Nonnull;

import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

public class Main {

  public static void main(@Nonnull String[] args) {

    if (args.length > 0) {
      int port = Integer.parseInt(args[0]);
      System.out.println(">Socket:" + port);
      ServerSocket clientSocket;
      InputStream in;
      OutputStream out;

      // start the socket server
      try (ServerSocket serverSocket = new ServerSocket()) {
        serverSocket.bind(new InetSocketAddress(port));

        while (true) {
          Socket socket = serverSocket.accept();
          System.out.println(">new Connection from " + socket.getInetAddress());

          final JimpleLanguageServer server = new JimpleLanguageServer();
          Launcher<LanguageClient> launcher =
                  new Launcher.Builder<LanguageClient>()
                          .setLocalService(server)
                          .setRemoteInterface(LanguageClient.class)
                          .setInput(logStream(socket.getInputStream(), "serverOut"))
                          .setOutput(logStream(socket.getOutputStream(), "serverIn"))
                          .setExecutorService(Executors.newCachedThreadPool())
                          .traceMessages(new PrintWriter(System.out))     // TODO
                          .create();
          launcher.startListening();
          server.connect(launcher.getRemoteProxy());
        }

      } catch (IOException exception) {
        exception.printStackTrace();
      }

    } else {
      System.out.println(">STDINOUT");
      JimpleLanguageServer server = new JimpleLanguageServer();
      Launcher<LanguageClient> l =
              LSPLauncher.createServerLauncher(
                      server, logStream(System.in, "serverOut"), logStream(System.out, "serverIn"));
      l.startListening();
      server.connect(l.getRemoteProxy());
    }
  }

  static InputStream logStream(InputStream is, String logFileName) {
    File log;
    try {
      log = File.createTempFile(logFileName, ".txt");
      return new TeeInputStream(is, new FileOutputStream(log));
    } catch (IOException e) {
      return is;
    }
  }

  static OutputStream logStream(OutputStream os, String logFileName) {
    File log;
    try {
      log = File.createTempFile(logFileName, ".txt");
      return new TeeOutputStream(os, new FileOutputStream(log));
    } catch (IOException e) {
      return os;
    }
  }
}
