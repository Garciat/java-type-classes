package com.garciat.typeclasses;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.runtime.RuntimeWitnessConstructor;
import com.garciat.typeclasses.runtime.RuntimeWitnessSystem;
import com.garciat.typeclasses.types.Either;
import java.util.List;

public final class TypeClasses {
  private TypeClasses() {}

  public static <T> T witness(Ty<T> ty) {
    Object instance =
        switch (RuntimeWitnessSystem.resolve(ty.type(), TypeClasses::invoke)) {
          case Either.Right(var r) -> r;
          case Either.Left(var error) ->
              throw new WitnessResolutionException(RuntimeWitnessSystem.format(error));
        };

    @SuppressWarnings("unchecked")
    T typedInstance = (T) instance;
    return typedInstance;
  }

  private static Object invoke(RuntimeWitnessConstructor ctor, List<Object> args) {
    try {
      return ctor.java().invoke(null, args.toArray());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static class WitnessResolutionException extends RuntimeException {
    private WitnessResolutionException(String message) {
      super(message);
    }
  }
}
