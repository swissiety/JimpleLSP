package magpiebridge.jimplelsp;

enum SemanticTokenTypeEnum {
  Namespace,
  Type,
  Class,
  Enum,
  Interface,
  Struct,
  TypeParameter,
  Parameter,
  Variable,
  Property,
  EnumMember,
  Event,
  Function,
  Member,
  Macro,
  Keyword,
  Modifier,
  Comment,
  String,
  Number,
  Regexp,
  Operator;

  private SemanticTokenTypeEnum() {
  }

  @Override
  public String toString() {
    String name = name();
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }
}
