package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.utils.Rose;
import com.garciat.typeclasses.impl.utils.ZeroOneMore;
import com.garciat.typeclasses.types.Either;
import com.garciat.typeclasses.types.Maybe;
import com.garciat.typeclasses.types.Pair;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class Resolution {
  private Resolution() {}

  public static <T, C> Either<Failure<T, C>, Rose<C>> resolve(
      Function<T, List<C>> next, BiFunction<T, C, Maybe<List<T>>> deps, T target) {
    List<C> options = next.apply(target);

    return switch (ZeroOneMore.of(Maybe.mapMaybe(options, matching(deps, target)))) {
      case ZeroOneMore.Zero() -> Either.left(new NotFound<>(target));
      case ZeroOneMore.More(var matches) ->
          Either.left(new Ambiguous<>(target, matches.stream().map(Pair::fst).toList()));
      case ZeroOneMore.One(Pair(var c, var ts)) ->
          Either.traverse(ts, t -> resolve(next, deps, t))
              .map(children -> Rose.of(c, children))
              .mapLeft(f -> new Nested<>(target, f));
    };
  }

  private static <T, C> Function<C, Maybe<Pair<C, List<T>>>> matching(
      BiFunction<T, C, Maybe<List<T>>> deps, T t) {
    return c -> deps.apply(t, c).map(ts -> new Pair<>(c, ts));
  }

  public sealed interface Failure<T, C> {}

  public record NotFound<T, C>(T target) implements Failure<T, C> {}

  public record Ambiguous<T, C>(T target, List<C> candidates) implements Failure<T, C> {}

  public record Nested<T, C>(T target, Failure<T, C> cause) implements Failure<T, C> {}
}
