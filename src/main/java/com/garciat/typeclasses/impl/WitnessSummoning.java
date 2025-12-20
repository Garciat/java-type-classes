package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.types.Either;
import java.lang.reflect.Type;

public final class WitnessSummoning {
  private WitnessSummoning() {}

  public static Either<SummonError, Object> summon(Type target) {
    RuntimeWitnessSystem system = new RuntimeWitnessSystem();

    return WitnessResolution.resolve(system, system.parse(target))
        .<SummonError>mapLeft(SummonError.ResolutionError::new)
        .map(WitnessInstantiation::compile)
        .flatMap(
            expr ->
                WitnessInstantiation.interpret(expr).mapLeft(SummonError.InstantiationError::new));
  }

  public sealed interface SummonError {
    record ResolutionError(Resolution.Failure<ParsedType, WitnessConstructor> error)
        implements SummonError {}

    record InstantiationError(WitnessInstantiation.InstantiationError error)
        implements SummonError {}

    default String format() {
      return switch (this) {
        case ResolutionError(var error) -> WitnessResolution.format(error);
        case InstantiationError(var error) -> error.format();
      };
    }
  }
}
