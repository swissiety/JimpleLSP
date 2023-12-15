package com.github.swissiety.jimplelsp.workingtree;

import com.ibm.wala.classLoader.SourceFileModule;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;


/**
 *
 *      Manages a copy of the actual workingDirectory to allow e.g. a longer running analysis to do its magic while not blocking the language client
 *
 * */
public class ShadowedWorkingTree extends WorkingTree{

    private Map<URI, SourceFileModule> sourceFileModules;
    /** Server-side URI string mapped to client-side URI string. */
    private Map<String, String> serverClientUri;

    /**
     * Instantiates a new source file manager.
     *
     * @param language        the language
     * @param serverClientUri the server client uri
     */
    public ShadowedWorkingTree(String language, Map<String, String> serverClientUri) {
        super(language);
        this.sourceFileModules = new HashMap<>();
        this.serverClientUri = serverClientUri;
    }


/*
    public void generateSourceFileModule(URI clientUri, VersionedFile versionedFile) {
        SourceFileModule sourceFile = null;
        try {
            File file = File.createTempFile("temp", getFileSuffix());
            file.deleteOnExit();
            String text = versionedFile.getContent();
            TemporaryFile.stringToFile(file, text);
            String[] strs = clientUri.toString().split("/");
            String className = strs[strs.length - 1];
            sourceFile = new SourceFileModule(file, className, null);
            this.sourceFileModules.put(clientUri, sourceFile);
            URI serverUri = Paths.get(file.toURI()).toUri();
            // store the mapping from server-side URI to client-side URI.
            this.serverClientUri.put(serverUri.toString(), clientUri.toString());
            if (serverUri.toString().startsWith("file:///")) {
                this.serverClientUri.put(
                        "file:/" + serverUri.toString().substring(8), clientUri.toString());
            }
        } catch (IOException e) {
            // FIXME
            e.printStackTrace();
        }
    }
*/


    /** Delete all server-side source files sent by the client. */
    public void cleanUp() {
        for (String file : this.serverClientUri.keySet()) {
            try {
                Files.deleteIfExists(Paths.get(new URI(file)));
            } catch (IOException | URISyntaxException e) {
                // FIXME!
                e.printStackTrace();
            }
        }
    }
}
