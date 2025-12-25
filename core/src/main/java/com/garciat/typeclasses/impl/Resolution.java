package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.utils.Either;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.impl.utils.Maybe;
import com.garciat.typeclasses.impl.utils.Unit;
import com.garciat.typeclasses.impl.utils.ZeroOneMore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.garciat.typeclasses.impl.utils.Sets.difference;
import static com.garciat.typeclasses.impl.utils.Unit.unit;
import static java.util.stream.Collectors.toSet;

public final class Resolution {
  private Resolution() {}

  public sealed interface Result<M, V, C, P> {
    record Node<M, V, C, P>(Match<M, V, C, P> match, List<Result<M, V, C, P>> children)
        implements Result<M, V, C, P> {}

    record LazyLookup<M, V, C, P>(ParsedType<V, C, P> target) implements Result<M, V, C, P> {}

    record LazyWrap<M, V, C, P>(Result<M, V, C, P> under) implements Result<M, V, C, P> {}
  }

  public static <M, V, C, P> Either<Failure<M, V, C, P>, Result<M, V, C, P>> resolve(
      Function<C, List<WitnessConstructor<M, V, C, P>>> constructors, ParsedType<V, C, P> target) {
    return resolveRec(new HashSet<>(), constructors, target);
  }

  private static <M, V, C, P> Either<Failure<M, V, C, P>, Result<M, V, C, P>> resolveRec(
      Set<ParsedType<V, C, P>> seen,
      Function<C, List<WitnessConstructor<M, V, C, P>>> constructors,
      ParsedType<V, C, P> target) {
    if (target instanceof ParsedType.Lazy(var under)) {
      if (seen.contains(target)) {
        return Either.right(new Result.LazyLookup<>(under));
      } else {
        seen.add(target);
        var out =
            resolveRec(seen, constructors, under).<Result<M, V, C, P>>map(Result.LazyWrap::new);
        seen.remove(target);
        return out;
      }
    }

    var witnesses = findWitnesses(constructors, target).stream().distinct().toList();

    var candidates =
        OverlappingInstances.reduce(Maybe.mapMaybe(witnesses, ctor -> match(ctor, target)));

    return switch (ZeroOneMore.of(candidates)) {
      case ZeroOneMore.Zero() -> Either.left(new Failure.NotFound<>(target, witnesses));
      case ZeroOneMore.More(var matches) -> Either.left(new Failure.Ambiguous<>(target, matches));
      case ZeroOneMore.One(var match) -> {
        Set<ParsedType.Var<V, C, P>> unboundTypeParams =
            match.dependencies().stream().flatMap(Free::freeVars).collect(toSet());

        Set<ParsedType.Var<V, C, P>> outTypeParams =
            unboundTypeParams.stream().filter(ParsedType.Var::isOut).collect(toSet());

        if (!outTypeParams.equals(unboundTypeParams)) {
          // TODO: Better error reporting
          var problem = difference(unboundTypeParams, outTypeParams);
          throw new IllegalStateException(
              "Cannot resolve witness for type "
                  + target.format()
                  + " because constructor "
                  + match.ctor().format()
                  + " has unbound non-covariant type parameters: "
                  + problem.stream()
                      .map(ParsedType.Var::format)
                      .collect(Collectors.joining(", ", "[", "]")));
        }

        Map<ParsedType.Var<V, C, P>, ParsedType<V, C, P>> substitution = new HashMap<>();

        for (int i = 0; i < match.dependencies().size(); i++) {
          var dep = match.dependencies().get(i);
          var src = match.ctor().paramTypes().get(i);

          if (dep.equals(src)) {
            // This dependency did not make progress
            // Trying to resolve it would just fail
            continue;
          }

          if (Free.outVars(dep).findAny().isEmpty()) {
            // This dependency has no free type variables
            // No need to resolve it
            continue;
          }

          switch (resolveRec(seen, constructors, dep)) {
            case Either.Right(Result.Node(var possible, _)) -> {
              switch (Unification.unify(dep, possible.witnessType())) {
                case Maybe.Just(var child) -> substitution.putAll(child);
                case Maybe.Nothing() ->
                    throw new IllegalStateException(
                        "Resolved dependency type "
                            + possible.witnessType().format()
                            + " does not unify with expected type "
                            + dep.format()
                            + " for constructor "
                            + match.ctor().format());
              }
            }
            case Either.Right(_) ->
                throw new IllegalStateException(
                    "Cannot resolve dependency type "
                        + dep.format()
                        + " for constructor "
                        + match.ctor().format()
                        + ": cyclic dependency detected");
            case Either.Left(var error) ->
                throw new IllegalStateException(
                    "Cannot resolve dependency type "
                        + dep.format()
                        + " for constructor "
                        + match.ctor().format()
                        + ":\n"
                        + error.format().indent(2));
          }
        }

        if (!substitution.keySet().containsAll(unboundTypeParams)) {
          var problem = difference(unboundTypeParams, substitution.keySet());
          throw new IllegalStateException(
              "Cannot resolve witness for type "
                  + target.format()
                  + " because constructor "
                  + match.ctor().format()
                  + " has unbound type parameters: "
                  + problem.stream()
                      .map(ParsedType.Var::format)
                      .collect(Collectors.joining(", ", "[", "]")));
        }

        List<ParsedType<V, C, P>> resolvedDependencies =
            Unification.substituteAll(substitution, match.dependencies());

        yield Either.traverse(resolvedDependencies, t -> resolveRec(seen, constructors, t))
            .<Result<M, V, C, P>>map(
                children ->
                    new Result.Node<>(
                        new Match<>(
                            match.ctor(),
                            resolvedDependencies,
                            Unification.substitute(substitution, match.witnessType())),
                        children))
            .mapLeft(f -> new Failure.Nested<>(target, f));
      }
    };
  }

  private static <M, V, C, P> Maybe<Match<M, V, C, P>> match(
      WitnessConstructor<M, V, C, P> ctor, ParsedType<V, C, P> target) {
    return Unification.unify(ctor.returnType(), target)
        .map(
            substitution ->
                new Match<>(
                    ctor,
                    Unification.substituteAll(substitution, ctor.paramTypes()),
                    Unification.substitute(substitution, ctor.returnType())));
  }

  private static <M, V, C, P> List<WitnessConstructor<M, V, C, P>> findWitnesses(
      Function<C, List<WitnessConstructor<M, V, C, P>>> constructors, ParsedType<V, C, P> target) {
    return switch (target) {
      case ParsedType.App(var fun1, ParsedType.App(var fun2, _)) ->
          Lists.concat(findWitnesses(constructors, fun1), findWitnesses(constructors, fun2));
      case ParsedType.App(var fun, var arg) ->
          Lists.concat(findWitnesses(constructors, fun), findWitnesses(constructors, arg));
      case ParsedType.Const<V, C, P> c -> constructors.apply(c.repr());
      case ParsedType.Lazy(var under) -> findWitnesses(constructors, under);
      case ParsedType.Var(_, _),
          ParsedType.ArrayOf(_),
          ParsedType.Primitive(_),
          ParsedType.Wildcard() ->
          List.of();
    };
  }

  public sealed interface Failure<M, V, C, P> {
    record NotFound<M, V, C, P>(
        ParsedType<V, C, P> target, List<WitnessConstructor<M, V, C, P>> witnesses)
        implements Failure<M, V, C, P> {}

    record Ambiguous<M, V, C, P>(ParsedType<V, C, P> target, List<Match<M, V, C, P>> candidates)
        implements Failure<M, V, C, P> {}

    record Nested<M, V, C, P>(ParsedType<V, C, P> target, Failure<M, V, C, P> cause)
        implements Failure<M, V, C, P> {}

    default String format() {
      return switch (this) {
        case NotFound(var target, var candidates) ->
            "No witnesses found for type: "
                + target.format()
                + (candidates.isEmpty()
                    ? "No witnesses available."
                    : "\nAvailable witnesses:\n"
                        + candidates.stream()
                            .map(WitnessConstructor::format)
                            .collect(Collectors.joining("\n"))
                            .indent(2));
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
