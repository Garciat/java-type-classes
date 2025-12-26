package com.garciat.typeclasses.impl;

import java.util.stream.Stream;

public final class Types {
  private Types() {}

  public static <V, C, P> Stream<ParsedType.Var<V, C, P>> findOutVars(ParsedType<V, C, P> type) {
    return switch (type) {
      case ParsedType.Var(_) -> Stream.of();
      case ParsedType.Out(ParsedType.Var<V, C, P> v) -> Stream.of(v);
      case ParsedType.Out(_) -> Stream.of();
      case ParsedType.App(var fun, var arg) -> Stream.concat(findOutVars(fun), findOutVars(arg));
      case ParsedType.ArrayOf(var elem) -> findOutVars(elem);
      case ParsedType.Lazy(var under) -> findOutVars(under);
      case ParsedType.Const(_, _), ParsedType.Primitive(_), ParsedType.Wildcard() -> Stream.of();
    };
  }

  public static <V, C, P> Stream<ParsedType.Var<V, C, P>> findVars(ParsedType<V, C, P> type) {
    return switch (type) {
      case ParsedType.Var<V, C, P> v -> Stream.of(v);
      case ParsedType.Out(_) -> Stream.of();
      case ParsedType.App(var fun, var arg) -> Stream.concat(findVars(fun), findVars(arg));
      case ParsedType.ArrayOf(var elem) -> findVars(elem);
      case ParsedType.Lazy(var under) -> findVars(under);
      case ParsedType.Const(_, _), ParsedType.Primitive(_), ParsedType.Wildcard() -> Stream.of();
    };
  }

  /** Unwraps one level of Out from the given type. */
  public static <C, P, V> ParsedType<V, C, P> unwrapOut1(ParsedType<V, C, P> type) {
    return switch (type) {
      case ParsedType.Out(var under) -> under;
      case ParsedType.App(var fun, var arg) ->
          new ParsedType.App<>(unwrapOut1(fun), unwrapOut1(arg));
      case ParsedType.ArrayOf(var elem) -> new ParsedType.ArrayOf<>(unwrapOut1(elem));
      case ParsedType.Lazy(var t) -> new ParsedType.Lazy<>(unwrapOut1(t));
      case ParsedType.Var<V, C, P> v -> v;
      case ParsedType.Primitive<V, C, P> p -> p;
      case ParsedType.Const<V, C, P> c -> c;
      case ParsedType.Wildcard<V, C, P> w -> w;
    };
  }
}
