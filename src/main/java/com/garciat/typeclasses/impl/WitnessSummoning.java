package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.WitnessRule.ContextInstance;
import com.garciat.typeclasses.types.Either;
import java.util.List;

public final class WitnessSummoning {
  private WitnessSummoning() {}

  public static Either<SummonError, Object> summon(
      ParsedType target, List<ContextInstance> context) {
    return WitnessResolution.resolve(target, context)
        .<SummonError>mapLeft(SummonError.Resolution::new)
        .map(WitnessInstantiation::compile)
        .flatMap(
            expr ->
                WitnessInstantiation.interpret(
                        WitnessInstantiation.InterpretContext.of(context), expr)
                    .mapLeft(SummonError.Instantiation::new));
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
