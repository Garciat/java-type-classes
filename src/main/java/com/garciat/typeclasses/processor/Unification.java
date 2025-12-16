package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.impl.utils.Maps;
import com.garciat.typeclasses.types.Maybe;
import com.garciat.typeclasses.types.Pair;
import java.util.List;
import java.util.Map;

public final class Unification {
  private Unification() {}

  public static Maybe<Map<ParsedType.Var, ParsedType>> unify(ParsedType t1, ParsedType t2) {
    return switch (Pair.of(t1, t2)) {
      case Pair<ParsedType, ParsedType>(ParsedType.Var var1, ParsedType.Primitive p) ->
          Maybe.nothing(); // no primitives in generics
      case Pair<ParsedType, ParsedType>(ParsedType.Var var1, var t) -> Maybe.just(Map.of(var1, t));
      case Pair<ParsedType, ParsedType>(ParsedType.Const const1, ParsedType.Const const2)
          when const1.equals(const2) ->
          Maybe.just(Map.of());
      case Pair<ParsedType, ParsedType>(
              ParsedType.App(var fun1, var arg1),
              ParsedType.App(var fun2, var arg2)) ->
          Maybe.apply(Maps::merge, unify(fun1, fun2), unify(arg1, arg2));
      case Pair<ParsedType, ParsedType>(
              ParsedType.ArrayOf(var elem1),
              ParsedType.ArrayOf(var elem2)) ->
          unify(elem1, elem2);
      case Pair<ParsedType, ParsedType>(
              ParsedType.Primitive(var prim1),
              ParsedType.Primitive(var prim2))
          when prim1.equals(prim2) ->
          Maybe.just(Map.of());
      default -> Maybe.nothing();
    };
  }

  public static ParsedType substitute(Map<ParsedType.Var, ParsedType> map, ParsedType type) {
    return switch (type) {
      case ParsedType.Var var -> map.getOrDefault(var, var);
      case ParsedType.App(var fun, var arg) ->
          new ParsedType.App(substitute(map, fun), substitute(map, arg));
      case ParsedType.ArrayOf var -> new ParsedType.ArrayOf(substitute(map, var.elementType()));
      case ParsedType.Primitive p -> p;
      case ParsedType.Const c -> c;
    };
  }

  public static List<ParsedType> substituteAll(
      Map<ParsedType.Var, ParsedType> map, List<ParsedType> types) {
    return types.stream().map(t -> substitute(map, t)).toList();
  }
}
