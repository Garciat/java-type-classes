package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.types.Either;
import java.lang.reflect.Type;

public final class WitnessSummoning {
  private WitnessSummoning() {}

  public static Either<SummonError, Object> summon(Type target) {
    RuntimeWitnessSystem system = new RuntimeWitnessSystem();

    return WitnessResolution.resolve(system, system.parse(target))
        .<SummonError>mapLeft(SummonError.Resolution::new)
        .map(WitnessInstantiation::compile)
        .flatMap(
            expr -> WitnessInstantiation.interpret(expr).mapLeft(SummonError.Instantiation::new));
  }

  public sealed interface SummonError {
    record Resolution(WitnessResolution.ResolutionError error) implements SummonError {}

    record Instantiation(WitnessInstantiation.InstantiationError error) implements SummonError {}

    default String format() {
      return switch (this) {
        case Resolution(WitnessResolution.ResolutionError error) -> error.format();
        case Instantiation(WitnessInstantiation.InstantiationError error) -> error.format();
      };
    }
  }
}
