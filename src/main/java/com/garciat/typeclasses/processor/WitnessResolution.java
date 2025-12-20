package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.impl.Resolution;
import com.garciat.typeclasses.impl.utils.Rose;
import com.garciat.typeclasses.types.Either;

public final class WitnessResolution {
  private WitnessResolution() {}

  public static Either<Resolution.Failure<ParsedType, WitnessConstructor>, Rose<WitnessConstructor>>
      resolve(StaticWitnessSystem system, ParsedType target) {
    return Resolution.resolve(
        t -> OverlappingInstances.reduce(system.findRules(t)),
        (t, c) ->
            Unification.unify(c.returnType(), t)
                .map(map -> Unification.substituteAll(map, c.paramTypes())),
        target);
  }

  public static String format(Resolution.Failure<ParsedType, WitnessConstructor> error) {
    return Resolution.format(ParsedType::format, WitnessConstructor::format, error);
  }
}
