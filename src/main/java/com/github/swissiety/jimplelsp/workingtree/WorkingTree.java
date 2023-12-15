package com.github.swissiety.jimplelsp.workingtree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import javax.annotation.Nonnull;

/**
 * This class manages all files of a given language sent from the language client to server.
 *
 * @author Linghui Luo, Markus Schmidt
 */
public class WorkingTree {

    /** The language. */
    private final String language;
    /** Client-side URI mapped to versioned source file. */
    private final Map<String, VersionedFile> versionedFiles;

    /**
     * Instantiates a new source file manager.
     *
     * @param language the language
     */
    public WorkingTree(@Nonnull String language) {
        this.language = language;
        this.versionedFiles = new HashMap<>();
    }

    /**
     * Add the opened file to versionedFiles and generate source file module for it.
     *
     * @param params the params
     */
    public void didOpen(DidOpenTextDocumentParams params) {
        TextDocumentItem doc = params.getTextDocument();
        if (!doc.getLanguageId().equals(language)) {
            return;
        }

        String uri = doc.getUri();
        VersionedFile sourceFile = new InMemoryVersionedFileImpl(uri, doc.getText(), doc.getVersion());
         //this.fileStates.put(clientUri, FileState.OPENED);
        this.versionedFiles.put(uri, sourceFile);
    }

    /**
     * Update the changed file and generate source file module for updated file.
     *
     * @param params the params
     */
    public void didChange(DidChangeTextDocumentParams params) {
        VersionedTextDocumentIdentifier doc = params.getTextDocument();
        String uri = doc.getUri();
        URI clientUri = URI.create(uri);
        // this.fileStates.put(clientUri, FileState.CHANGED);
        VersionedFile existFile = versionedFiles.get(URI.create(uri));
        int newVersion = doc.getVersion();
        if (newVersion > existFile.getVersion()) {
            String existText = existFile.getContent();
            String newText = existText;
            for (TextDocumentContentChangeEvent change : params.getContentChanges()) {
                if (change.getRange() == null) {
                    // the nextText should be the full context of the file.
                    newText = change.getText();
                } else {
                    newText = replaceText(newText, change);
                }
            }
            if (newText != null) {
                VersionedFile newFile = new InMemoryVersionedFileImpl(uri, newText, newVersion);
                this.versionedFiles.put(uri, newFile);
            }
        }
    }

    public void didSave(DidSaveTextDocumentParams params) {
        TextDocumentIdentifier doc = params.getTextDocument();
        String uri = doc.getUri();
        // this.fileStates.put(clientUri, FileState.SAVED);
    }

    /**
     * Replace the old text according to the change.
     *
     * @param text the text
     * @param change the change
     * @return the string
     */
    private String replaceText(String text, TextDocumentContentChangeEvent change) {
        try {
            Range range = change.getRange();
            BufferedReader reader = new BufferedReader(new StringReader(text));
            StringWriter writer = new StringWriter();
            int line = 0;
            while (line < range.getStart().getLine()) {
                writer.write(reader.readLine() + '\n');
                line++;
            }
            for (int character = 0; character < range.getStart().getCharacter(); character++) {
                writer.write(reader.read());
            }
            // write the changed text
            writer.write(change.getText());
            // skip the old text
            reader.skip(change.getRangeLength());
            int next = reader.read();
            while (next != -1) {
                writer.write(next);
                next = reader.read();
            }
            return writer.toString();
        } catch (IOException e) {
            // FIXME log(e);
        }
        return null;
    }


    /**
     * Gets the versioned files.
     *
     * @return the versioned files
     */
    public Map<String, VersionedFile> getVersionedFiles() {
        return versionedFiles;
    }

    public VersionedFile get(String uri) {
        return versionedFiles.get(uri);
    }
}