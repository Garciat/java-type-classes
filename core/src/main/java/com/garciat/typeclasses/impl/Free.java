package com.garciat.typeclasses.impl;

import java.util.stream.Stream;

public final class Free {
  private Free() {}

  public static <V, C, P> Stream<ParsedType.Var<V, C, P>> outVars(ParsedType<V, C, P> type) {
    return freeVars(type).filter(ParsedType.Var::isOut);
  }

  public static <V, C, P> Stream<ParsedType.Var<V, C, P>> freeVars(ParsedType<V, C, P> type) {
    return switch (type) {
      case ParsedType.Var<V, C, P> v -> Stream.of(v);
      case ParsedType.App(var fun, var arg) -> Stream.concat(freeVars(fun), freeVars(arg));
      case ParsedType.ArrayOf(var elem) -> freeVars(elem);
      case ParsedType.Lazy(var under) -> freeVars(under);
      case ParsedType.Const(_, _), ParsedType.Primitive(_), ParsedType.Wildcard() -> Stream.of();
    };
  }
}
