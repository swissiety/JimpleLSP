package com.github.swissiety.jimplelsp.workingtree;

public interface VersionedFile {

    String getUriStr();

    int getVersion();

    String getContent();
}
