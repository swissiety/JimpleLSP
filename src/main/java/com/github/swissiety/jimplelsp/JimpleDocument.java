package com.github.swissiety.jimplelsp;

public class JimpleDocument {

  private final String uri;
  private final String text;

  public String getUri() {
    return uri;
  }

  public String getText() {
    return text;
  }

  public JimpleDocument(String uri, String text) {
    this.uri = uri;
    this.text = text;
  }
}
