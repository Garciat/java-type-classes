package com.garciat.typeclasses;

import com.garciat.typeclasses.api.Lazy;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.impl.Match;
import com.garciat.typeclasses.impl.ParsedType;
import com.garciat.typeclasses.impl.Resolution;
import com.garciat.typeclasses.impl.utils.Either;
import com.garciat.typeclasses.runtime.Runtime;
import com.garciat.typeclasses.runtime.RuntimeWitnessSystem;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TypeClasses {
  private TypeClasses() {}

  public static <T> T witness(Ty<T> ty) {
    Resolution.Result<Runtime.Method, Runtime.Var, Runtime.Const, Runtime.Prim> methodTree =
        switch (RuntimeWitnessSystem.resolve(ty.type())) {
          case Either.Right(var r) -> r;
          case Either.Left(var error) -> throw new WitnessResolutionException(error.format());
        };

    Object instance = walk(new HashMap<>(), methodTree);

    @SuppressWarnings("unchecked")
    T typedInstance = (T) instance;
    return typedInstance;
  }

  private static Object walk(
      Map<ParsedType<Runtime.Var, Runtime.Const, Runtime.Prim>, Object> cache,
      Resolution.Result<Runtime.Method, Runtime.Var, Runtime.Const, Runtime.Prim> tree) {
    return switch (tree) {
      case Resolution.Result.Node(var match, var dependencies) -> {
        Object[] args = dependencies.stream().map(dep -> walk(cache, dep)).toArray();

        Object instance = invoke(match, args);

        cache.put(match.witnessType(), instance);

        yield instance;
      }
      case Resolution.Result.LazyLookup(var target) ->
          (Lazy<Object>)
              () ->
                  Optional.ofNullable(cache.get(target))
                      .orElseThrow(
                          () ->
                              new WitnessResolutionException(
                                  "BUG: expected cached instance for %s"
                                      .formatted(target.format())));
      case Resolution.Result.LazyWrap(var under) -> (Lazy<Object>) () -> walk(cache, under);
    };
  }

  private static Object invoke(
      Match<Runtime.Method, Runtime.Var, Runtime.Const, Runtime.Prim> match, Object[] args) {
    try {
      return match.ctor().method().java().invoke(null, args);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("BUG: expected witness constructor method to be public", e);
    } catch (InvocationTargetException e) {
      throw new WitnessResolutionException(
          "Witness constructor %s threw an exception while resolving %s"
              .formatted(match.ctor().method(), match.witnessType().format()),
          e.getTargetException());
    }
  }

  public static class WitnessResolutionException extends RuntimeException {
    private WitnessResolutionException(String message) {
      super(message);
    }

    private WitnessResolutionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
