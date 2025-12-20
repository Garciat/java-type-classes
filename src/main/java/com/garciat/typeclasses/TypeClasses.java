package com.garciat.typeclasses;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.impl.utils.Rose;
import com.garciat.typeclasses.runtime.RuntimeWitnessConstructor;
import com.garciat.typeclasses.runtime.RuntimeWitnessInstantiation;
import com.garciat.typeclasses.runtime.RuntimeWitnessSystem;
import com.garciat.typeclasses.types.Either;

public final class TypeClasses {
  private TypeClasses() {}

  public static <T> T witness(Ty<T> ty) {
    Rose<RuntimeWitnessConstructor> plan =
        switch (RuntimeWitnessSystem.resolve(ty.type())) {
          case Either.Right(var r) -> r;
          case Either.Left(var error) ->
              throw new WitnessResolutionException(RuntimeWitnessSystem.format(error));
        };

    RuntimeWitnessInstantiation.Expr expr = RuntimeWitnessInstantiation.compile(plan);

    return switch (RuntimeWitnessInstantiation.interpret(expr)) {
      case Either.Right(var instance) -> {
        @SuppressWarnings("unchecked")
        T typedInstance = (T) instance;
        yield typedInstance;
      }
      case Either.Left(var error) -> throw new WitnessResolutionException(error.format());
    };
  }

  public static class WitnessResolutionException extends RuntimeException {
    private WitnessResolutionException(String message) {
      super(message);
    }
  }
}
