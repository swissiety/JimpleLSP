package com.github.swissiety.jimplelsp.workingtree;


import javax.annotation.Nonnull;

public class InMemoryVersionedFileImpl implements VersionedFile {

    private final String uri;
    private final String data;
    private final int version;

    public InMemoryVersionedFileImpl(@Nonnull String uri, @Nonnull String data, int version) {
        this.version = version;
        this.uri = uri;
        this.data = data;
    }

    @Override
    public String getUriStr() {
        return uri;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public String getContent() {
        return data;
    }
}
