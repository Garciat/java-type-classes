package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.ParsedType.App;
import com.garciat.typeclasses.impl.ParsedType.ArrayOf;
import com.garciat.typeclasses.impl.ParsedType.Const;
import com.garciat.typeclasses.impl.ParsedType.Lazy;
import com.garciat.typeclasses.impl.ParsedType.Primitive;
import com.garciat.typeclasses.impl.ParsedType.Var;
import com.garciat.typeclasses.impl.ParsedType.Wildcard;
import com.garciat.typeclasses.impl.utils.Maps;
import com.garciat.typeclasses.impl.utils.Maybe;
import com.garciat.typeclasses.impl.utils.Pair;
import java.util.List;
import java.util.Map;

public final class Unification {
  private Unification() {}

  /**
   * This is asymmetric: we check if the second type can be made to match the first, but not vice
   * versa.
   */
  public static <V, C, P> Maybe<Map<Var<V, C, P>, ParsedType<V, C, P>>> unify(
      ParsedType<V, C, P> t1, ParsedType<V, C, P> t2) {
    return switch (Pair.of(t1, t2)) {
      case Pair(Lazy(var x), var t) -> unify(x, t);
      case Pair(var t, Lazy(var x)) -> unify(t, x);
      case Pair(Var(_, _), Primitive(_)) -> Maybe.nothing(); // no primitives in generics
      case Pair(Var<V, C, P> v, var t) -> Maybe.just(Map.of(v, t));
      case Pair(Const(var repr1, _), Const(var repr2, _)) when repr1.equals(repr2) ->
          Maybe.just(Map.of());
      case Pair(App(var fun1, var arg1), App(var fun2, var arg2)) ->
          Maybe.apply(Maps::merge, unify(fun1, fun2), unify(arg1, arg2));
      case Pair(ArrayOf(var elem1), ArrayOf(var elem2)) -> unify(elem1, elem2);
      case Pair(Primitive(var prim1), Primitive(var prim2)) when prim1.equals(prim2) ->
          Maybe.just(Map.of());
      case Pair(Wildcard(), _) -> Maybe.just(Map.of());
      case Pair(var t, Var<V, C, P> v) when v.isOut() -> Maybe.just(Map.of(v, t));
      case Pair(_, _) -> Maybe.nothing();
    };
  }

  public static <V, C, P> ParsedType<V, C, P> substitute(
      Map<Var<V, C, P>, ParsedType<V, C, P>> map, ParsedType<V, C, P> type) {
    return switch (type) {
      case Var<V, C, P> var -> map.getOrDefault(var, var);
      case App(var fun, var arg) -> new App<>(substitute(map, fun), substitute(map, arg));
      case ArrayOf(var elem) -> new ArrayOf<>(substitute(map, elem));
      case Lazy(var t) -> new Lazy<>(substitute(map, t));
      case Primitive<V, C, P> p -> p;
      case Const<V, C, P> c -> c;
      case Wildcard<V, C, P> w -> w;
    };
  }

  public static <V, C, P> List<ParsedType<V, C, P>> substituteAll(
      Map<Var<V, C, P>, ParsedType<V, C, P>> map, List<ParsedType<V, C, P>> types) {
    return types.stream().map(t -> substitute(map, t)).toList();
  }
}
