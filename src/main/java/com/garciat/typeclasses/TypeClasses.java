package com.garciat.typeclasses;

import com.garciat.typeclasses.api.Ctx;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.impl.ParsedType;
import com.garciat.typeclasses.impl.WitnessRule.ContextInstance;
import com.garciat.typeclasses.impl.WitnessSummoning;
import com.garciat.typeclasses.impl.WitnessSummoning.SummonError;
import com.garciat.typeclasses.types.Either;
import java.util.Arrays;
import java.util.List;

public final class TypeClasses {
  private TypeClasses() {}

  public static <T> T witness(Ty<T> ty, Ctx<?>... context) {
    return switch (WitnessSummoning.summon(ParsedType.parse(ty.type()), parseContext(context))) {
      case Either.Left<SummonError, Object>(SummonError error) ->
          throw new WitnessResolutionException(error);
      case Either.Right<SummonError, Object>(Object instance) -> {
        @SuppressWarnings("unchecked")
        T typedInstance = (T) instance;
        yield typedInstance;
      }
    };
  }

  private static List<ContextInstance> parseContext(Ctx<?>[] context) {
    return Arrays.stream(context)
        .map(ctx -> new ContextInstance(ctx.instance(), ParsedType.parse(ctx.type())))
        .toList();
  }

  public static class WitnessResolutionException extends RuntimeException {
    private WitnessResolutionException(SummonError error) {
      super(error.format());
    }
  }
}
