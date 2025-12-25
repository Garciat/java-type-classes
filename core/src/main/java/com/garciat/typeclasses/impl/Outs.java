package com.garciat.typeclasses.impl;

import java.util.stream.Stream;

public final class Outs {
  private Outs() {}

  public static <V, C, P> Stream<ParsedType.Out<V, C, P>> findOuts(ParsedType<V, C, P> type) {
    return switch (type) {
      case ParsedType.Var(_) -> Stream.of();
      case ParsedType.Out<V, C, P> v -> Stream.of(v);
      case ParsedType.App(var fun, var arg) -> Stream.concat(findOuts(fun), findOuts(arg));
      case ParsedType.ArrayOf(var elem) -> findOuts(elem);
      case ParsedType.Lazy(var under) -> findOuts(under);
      case ParsedType.Const(_, _), ParsedType.Primitive(_), ParsedType.Wildcard() -> Stream.of();
    };
  }

  public static <C, P, V> ParsedType<V, C, P> unwrapOut(ParsedType<V, C, P> type) {
    return switch (type) {
      case ParsedType.Out(var under) -> unwrapOut(under);
      case ParsedType.App(var fun, var arg) -> new ParsedType.App<>(unwrapOut(fun), unwrapOut(arg));
      case ParsedType.ArrayOf(var elem) -> new ParsedType.ArrayOf<>(unwrapOut(elem));
      case ParsedType.Lazy(var t) -> new ParsedType.Lazy<>(unwrapOut(t));
      case ParsedType.Var<V, C, P> v -> v;
      case ParsedType.Primitive<V, C, P> p -> p;
      case ParsedType.Const<V, C, P> c -> c;
      case ParsedType.Wildcard<V, C, P> w -> w;
    };
  }
}
