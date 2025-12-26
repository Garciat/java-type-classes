package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.utils.Lists;
import java.util.List;
import java.util.function.Function;

final class Witnesses {
  private Witnesses() {}

  static <M, V, C, P> List<WitnessConstructor<M, V, C, P>> findWitnesses(
      Function<C, List<WitnessConstructor<M, V, C, P>>> constructors, ParsedType<V, C, P> target) {
    return switch (target) {
      case ParsedType.App(var fun1, ParsedType.App(var fun2, _)) ->
          Lists.concat(findWitnesses(constructors, fun1), findWitnesses(constructors, fun2));
      case ParsedType.App(var fun, var arg) ->
          Lists.concat(findWitnesses(constructors, fun), findWitnesses(constructors, arg));
      case ParsedType.Const<V, C, P> c -> constructors.apply(c.repr());
      case ParsedType.Lazy(var under) -> findWitnesses(constructors, under);
      case ParsedType.Var(_),
          ParsedType.Out(_),
          ParsedType.ArrayOf(_),
          ParsedType.Primitive(_),
          ParsedType.Wildcard() ->
          List.of();
    };
  }
}
