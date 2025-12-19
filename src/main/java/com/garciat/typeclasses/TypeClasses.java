package com.garciat.typeclasses;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.impl.WitnessSummoning;
import com.garciat.typeclasses.impl.WitnessSummoning.SummonError;
import com.garciat.typeclasses.types.Either;

public final class TypeClasses {
  private TypeClasses() {}

  public static <T> T witness(Ty<T> ty) {
    return switch (WitnessSummoning.summon(ty.type())) {
      case Either.Left<SummonError, Object>(SummonError error) ->
          throw new WitnessResolutionException(error);
      case Either.Right<SummonError, Object>(Object instance) -> {
        @SuppressWarnings("unchecked")
        T typedInstance = (T) instance;
        yield typedInstance;
      }
    };
  }

  /**
   * Parameterless witness method that should be rewritten by the compiler. This method should never
   * be called at runtime; the compiler will replace it with the appropriate witness constructor
   * calls.
   */
  public static <T> T witness() {
    throw new AssertionError(
        "witness() should have been rewritten by the compiler. "
            + "Make sure the WitnessResolutionChecker annotation processor is enabled.");
  }

  public static class WitnessResolutionException extends RuntimeException {
    private WitnessResolutionException(SummonError error) {
      super(error.format());
    }
  }
}
