package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.impl.utils.Maps;
import com.garciat.typeclasses.processor.ParsedType.App;
import com.garciat.typeclasses.processor.ParsedType.ArrayOf;
import com.garciat.typeclasses.processor.ParsedType.Const;
import com.garciat.typeclasses.processor.ParsedType.Primitive;
import com.garciat.typeclasses.processor.ParsedType.Var;
import com.garciat.typeclasses.types.Maybe;
import com.garciat.typeclasses.types.Pair;
import java.util.List;
import java.util.Map;

public final class Unification {
  private Unification() {}

  public static Maybe<Map<Var, ParsedType>> unify(ParsedType t1, ParsedType t2) {
    return switch (Pair.of(t1, t2)) {
      case Pair(Var _, Primitive _) -> Maybe.nothing(); // no primitives in generics
      case Pair(Var v, var t) -> Maybe.just(Map.of(v, t));
      case Pair(Const const1, Const const2) when const1.equals(const2) -> Maybe.just(Map.of());
      case Pair(App(var fun1, var arg1), App(var fun2, var arg2)) ->
          Maybe.apply(Maps::merge, unify(fun1, fun2), unify(arg1, arg2));
      case Pair(ArrayOf(var elem1), ArrayOf(var elem2)) -> unify(elem1, elem2);
      case Pair(Primitive(var prim1), Primitive(var prim2)) when prim1.equals(prim2) ->
          Maybe.just(Map.of());
      default -> Maybe.nothing();
    };
  }

  public static ParsedType substitute(Map<Var, ParsedType> map, ParsedType type) {
    return switch (type) {
      case Var var -> map.getOrDefault(var, var);
      case App(var fun, var arg) -> new App(substitute(map, fun), substitute(map, arg));
      case ArrayOf(var elem) -> new ArrayOf(substitute(map, elem));
      case Primitive p -> p;
      case Const c -> c;
    };
  }

  public static List<ParsedType> substituteAll(Map<Var, ParsedType> map, List<ParsedType> types) {
    return types.stream().map(t -> substitute(map, t)).toList();
  }
}
