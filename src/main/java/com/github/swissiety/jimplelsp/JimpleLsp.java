package com.github.swissiety.jimplelsp;

import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Function;
import java.util.function.Supplier;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.ServerConfiguration;
import magpiebridge.util.MagpieMessageLogger;
import org.apache.commons.cli.*;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.validation.ReflectiveMessageValidator;

/** @author Markus Schmidt */
public class JimpleLsp {

  private static final String DEFAULT_PORT = "2403";
  private static CommandLine cmd = null;

  public static void main(String... args) throws IOException, InterruptedException {
    Options cliOptions =
        new Options()
            .addOption(
                "s",
                "socket",
                false,
                MessageFormat.format("run in socket mode, standard port is {0}", DEFAULT_PORT))
            .addOption(
                "p",
                "port",
                true,
                MessageFormat.format(
                    "sets the port for socket mode, standard port is {0}", DEFAULT_PORT));

    CommandLineParser parser = new DefaultParser();

    try {
      cmd = parser.parse(cliOptions, args);
    } catch (ParseException e) {
      e.printStackTrace();
      System.exit(1);
    }

    Supplier<MagpieServer> createServer =
        () -> {
          ServerConfiguration config = new ServerConfiguration();
          config.setDoAnalysisBySave(false);
          config.setDoAnalysisByOpen(false);
          config.setShowConfigurationPage(true, true);
          config.setMagpieMessageLogger(
              new MagpieMessageLogger() {
                @Override
                public Function<MessageConsumer, MessageConsumer> getWrapper() {
                  return (MessageConsumer c) -> {
                    MessageConsumer wrappedConsumer =
                        message -> {
                          String timeStamp =
                              new SimpleDateFormat("[HH:mm:ss:SS]").format(new Date());
                          // don't print! otherwise it breaks in stdio mode!
                          //     System.err.println(timeStamp + message);
                          new ReflectiveMessageValidator(c).consume(message);
                        };
                    return wrappedConsumer;
                  };
                }

                @Override
                public void cleanUp() {}
              });

          // to disable unnecessarily showing the control panel
          config.setShowConfigurationPage(false, false);

          MagpieServer server = new JimpleLspServer(config);
          /* TODO: e.g. extract apk on demand
          Map<String, String> conf = new HashMap<>();
          conf.put("Extract Apk", "extractJimpleFromAPK");
          server.setConfigurationOptions(conf);

          // TODO: Magpie: reuse supported language definitions
          String language = "jimple";
          ServerAnalysis analysis = new JimpleServerAnalysis();
          server.addAnalysis(Either.forLeft(analysis), language);
           */

          return server;
        };

    if (cmd.hasOption("socket")) {
      int port = Integer.parseInt(cmd.getOptionValue("port", DEFAULT_PORT));
      System.out.println("Socket:" + port);
      MagpieServer.launchOnSocketPort(port, createServer);
    } else {
      createServer.get().launchOnStdio();
    }
  }
}
