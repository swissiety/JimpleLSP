package com.github.swissiety.jimplelsp;

import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.cli.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.validation.ReflectiveMessageValidator;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.graalvm.polyglot.Language;

/**
 * @author Markus Schmidt
 */
public class JimpleLsp {

    private static final String DEFAULT_PORT = "2403";
    private static CommandLine cmd = null;

    public static void main(String... args) throws IOException, InterruptedException, ExecutionException {
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

        JimpleLspServer server = new JimpleLspServer();

        /* if (cmd.hasOption("socket")) {
            int port = Integer.parseInt(cmd.getOptionValue("port", DEFAULT_PORT));
            System.out.println("Socket:" + port);
           // FIXME LanguageServer.launchOnSocketPort(port, createServer);
        } else */ {

            Launcher<LanguageClient> l = LSPLauncher.createServerLauncher(server, System.in, System.out);
            Future<?> startListening = l.startListening();
            server.connectClient(l.getRemoteProxy());
            startListening.get();

        }
    }
}
