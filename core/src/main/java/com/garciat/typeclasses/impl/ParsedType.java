package com.garciat.typeclasses.impl;

import java.util.List;

public sealed interface ParsedType<V, C, P> {
  record Var<V, C, P>(V repr, boolean isOut) implements ParsedType<V, C, P> {}

  record App<V, C, P>(ParsedType<V, C, P> fun, ParsedType<V, C, P> arg)
      implements ParsedType<V, C, P> {}

  record ArrayOf<V, C, P>(ParsedType<V, C, P> elementType) implements ParsedType<V, C, P> {}

  record Const<V, C, P>(C repr, List<Var<V, C, P>> typeParams) implements ParsedType<V, C, P> {}

  record Primitive<V, C, P>(P repr) implements ParsedType<V, C, P> {}

  record Wildcard<V, C, P>() implements ParsedType<V, C, P> {}

  record Lazy<V, C, P>(ParsedType<V, C, P> under) implements ParsedType<V, C, P> {}

  default String format() {
    return switch (this) {
      case Var(var repr, var isOut) -> (isOut ? "&" : "") + repr.toString();
      case Const(var repr, var typeParams) ->
          repr.toString()
              + typeParams.stream()
                  .map(ParsedType::format)
                  .reduce((a, b) -> a + ", " + b)
                  .map(s -> "[" + s + "]")
                  .orElse("");
      case App(var fun, var arg) -> fun.format() + "(" + arg.format() + ")";
      case ArrayOf(var elem) -> elem.format() + "[]";
      case Primitive(var repr) -> repr.toString();
      case Wildcard() -> "?";
      case Lazy(var repr) -> "Lazy<" + repr.format() + ">";
    };
  }
}
