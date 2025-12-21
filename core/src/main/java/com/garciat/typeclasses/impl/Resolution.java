package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.utils.Either;
import com.garciat.typeclasses.impl.utils.Formatter;
import com.garciat.typeclasses.impl.utils.Maybe;
import com.garciat.typeclasses.impl.utils.Pair;
import com.garciat.typeclasses.impl.utils.ZeroOneMore;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Resolution {
  private Resolution() {}

  public static <T, C, R> Either<Failure<T, C>, R> resolve(
      Function<T, List<C>> next,
      BiFunction<T, C, Maybe<List<T>>> deps,
      BiFunction<C, List<R>, R> build,
      T target) {
    List<C> options = next.apply(target);

    return switch (ZeroOneMore.of(Maybe.mapMaybe(options, matching(deps, target)))) {
      case ZeroOneMore.Zero() -> Either.left(new NotFound<>(target));
      case ZeroOneMore.More(var matches) ->
          Either.left(new Ambiguous<>(target, matches.stream().map(Pair::fst).toList()));
      case ZeroOneMore.One(Pair(var c, var ts)) ->
          Either.traverse(ts, t -> resolve(next, deps, build, t))
              .map(children -> build.apply(c, children))
              .mapLeft(f -> new Nested<>(target, f));
    };
  }

  public static <T, C, R> Either<Failure<T, C>, R> resolve2(
      Function<T, List<C>> candidates,
      Function<C, List<T>> deps,
      BiFunction<C, List<R>, R> build,
      T target) {
    return switch (ZeroOneMore.of(candidates.apply(target))) {
      case ZeroOneMore.Zero() -> Either.left(new NotFound<>(target));
      case ZeroOneMore.More(var matches) -> Either.left(new Ambiguous<>(target, matches));
      case ZeroOneMore.One(var c) ->
          Either.traverse(deps.apply(c), t -> resolve2(candidates, deps, build, t))
              .map(children -> build.apply(c, children))
              .mapLeft(f -> new Nested<>(target, f));
    };
  }

  private static <T, C> Function<C, Maybe<Pair<C, List<T>>>> matching(
      BiFunction<T, C, Maybe<List<T>>> deps, T t) {
    return c -> deps.apply(t, c).map(ts -> new Pair<>(c, ts));
  }

  public static <T, C> String format(
      Formatter<T> formatT, Formatter<C> formatC, Failure<T, C> error) {
    return switch (error) {
      case NotFound(T target) -> "No witness found for type: " + formatT.apply(target);
      case Ambiguous(T target, List<C> candidates) ->
          "Ambiguous witnesses found for type: "
              + formatT.apply(target)
              + "\nCandidates:\n"
              + candidates.stream().map(formatC).collect(Collectors.joining("\n")).indent(2);
      case Nested(T target, Failure<T, C> cause) ->
          "While resolving witness for type: "
              + formatT.apply(target)
              + "\nCaused by: "
              + format(formatT, formatC, cause).indent(2);
    };
  }

  public sealed interface Failure<T, C> {}

  public record NotFound<T, C>(T target) implements Failure<T, C> {}

  public record Ambiguous<T, C>(T target, List<C> candidates) implements Failure<T, C> {}

  public record Nested<T, C>(T target, Failure<T, C> cause) implements Failure<T, C> {}
}
