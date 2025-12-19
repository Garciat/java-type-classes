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
   * Parameterless witness method that gets rewritten by the compiler plugin. This method should
   * never be called at runtime - the compiler plugin will replace calls to this method with direct
   * witness constructor invocations.
   *
   * @param <T> The witness type to resolve
   * @return The witness instance (at compile time, replaced with constructor calls)
   * @throws UnsupportedOperationException if called at runtime without compiler plugin
   */
  public static <T> T witness() {
    throw new UnsupportedOperationException(
        "witness() without parameters should only be used with the compiler plugin enabled. "
            + "Use witness(Ty<T>) for runtime resolution.");
  }

  public static class WitnessResolutionException extends RuntimeException {
    private WitnessResolutionException(SummonError error) {
      super(error.format());
    }
  }
}
