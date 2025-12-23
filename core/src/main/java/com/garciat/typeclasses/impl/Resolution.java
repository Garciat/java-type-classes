package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.utils.Either;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.impl.utils.Maybe;
import com.garciat.typeclasses.impl.utils.ZeroOneMore;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Resolution {
  private Resolution() {}

  public static <M, V, C, P, R> Either<Failure<M, V, C, P>, R> resolve(
      Function<ParsedType.Const<V, C, P>, List<WitnessConstructor<M, V, C, P>>> constructors,
      BiFunction<Match<M, V, C, P>, List<R>, R> build,
      ParsedType<V, C, P> target) {
    var candidates =
        OverlappingInstances.reduce(
            Maybe.mapMaybe(findWitnesses(target, constructors), ctor -> match(ctor, target)));

    return switch (ZeroOneMore.of(candidates)) {
      case ZeroOneMore.Zero() -> Either.left(new Failure.NotFound<>(target));
      case ZeroOneMore.More(var matches) -> Either.left(new Failure.Ambiguous<>(target, matches));
      case ZeroOneMore.One(var c) ->
          Either.traverse(c.dependencies(), t -> resolve(constructors, build, t))
              .map(children -> build.apply(c, children))
              .mapLeft(f -> new Failure.Nested<>(target, f));
    };
  }

  private static <M, V, C, P> List<WitnessConstructor<M, V, C, P>> findWitnesses(
      ParsedType<V, C, P> target,
      Function<ParsedType.Const<V, C, P>, List<WitnessConstructor<M, V, C, P>>> constructors) {
    return switch (target) {
      case ParsedType.App(var fun, var arg) ->
          Lists.concat(findWitnesses(fun, constructors), findWitnesses(arg, constructors));
      case ParsedType.Const<V, C, P> c -> constructors.apply(c);
      case ParsedType.Var(_),
          ParsedType.ArrayOf(_),
          ParsedType.Primitive(_),
          ParsedType.Wildcard() ->
          List.of();
    };
  }

  private static <M, V, C, P> Maybe<Match<M, V, C, P>> match(
      WitnessConstructor<M, V, C, P> ctor, ParsedType<V, C, P> target) {
    return Unification.unify(ctor.returnType(), target)
        .map(map -> Unification.substituteAll(map, ctor.paramTypes()))
        .map(dependencies -> new Match<>(ctor, dependencies, target));
  }

  public sealed interface Failure<M, V, C, P> {
    record NotFound<M, V, C, P>(ParsedType<V, C, P> target) implements Failure<M, V, C, P> {}

    record Ambiguous<M, V, C, P>(ParsedType<V, C, P> target, List<Match<M, V, C, P>> candidates)
        implements Failure<M, V, C, P> {}

    record Nested<M, V, C, P>(ParsedType<V, C, P> target, Failure<M, V, C, P> cause)
        implements Failure<M, V, C, P> {}

    default String format() {
      return switch (this) {
        case NotFound(var target) -> "No witness found for type: " + target.format();
        case Ambiguous(var target, var candidates) ->
            "Ambiguous witnesses found for type: "
                + target.format()
                + "\nCandidates:\n"
                + candidates.stream()
                    .map(Match::ctor)
                    .map(WitnessConstructor::format)
                    .collect(Collectors.joining("\n"))
                    .indent(2);
        case Nested(var target, var cause) ->
            "While resolving witness for type: "
                + target.format()
                + "\nCaused by: "
                + cause.format().indent(2);
      };
    }
  }
}
