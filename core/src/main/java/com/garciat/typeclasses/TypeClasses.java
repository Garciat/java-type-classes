package com.garciat.typeclasses;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.impl.Match;
import com.garciat.typeclasses.impl.utils.Either;
import com.garciat.typeclasses.runtime.Runtime;
import com.garciat.typeclasses.runtime.RuntimeWitnessSystem;
import java.util.List;

public final class TypeClasses {
  private TypeClasses() {}

  public static <T> T witness(Ty<T> ty) {
    Object instance =
        switch (RuntimeWitnessSystem.resolve(ty.type(), TypeClasses::invoke)) {
          case Either.Right(var r) -> r;
          case Either.Left(var error) -> throw new WitnessResolutionException(error.format());
        };

    @SuppressWarnings("unchecked")
    T typedInstance = (T) instance;
    return typedInstance;
  }

  private static Object invoke(
      Match<Runtime.Method, Runtime.Var, Runtime.Const, Runtime.Prim> match, List<Object> args) {
    try {
      return match.ctor().method().java().invoke(null, args.toArray());
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
