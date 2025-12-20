package com.garciat.typeclasses;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.impl.*;
import com.garciat.typeclasses.impl.utils.Rose;
import com.garciat.typeclasses.types.Either;

public final class TypeClasses {
  private TypeClasses() {}

  public static <T> T witness(Ty<T> ty) {
    RuntimeWitnessSystem system = new RuntimeWitnessSystem();

    Rose<WitnessConstructor> plan =
        switch (WitnessResolution.resolve(system, system.parse(ty.type()))) {
          case Either.Right(var r) -> r;
          case Either.Left(var error) ->
              throw new WitnessResolutionException(WitnessResolution.format(error));
        };

    WitnessInstantiation.Expr expr = WitnessInstantiation.compile(plan);

    return switch (WitnessInstantiation.interpret(expr)) {
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
