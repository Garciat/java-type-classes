package com.garciat.typeclasses.impl;

import static com.garciat.typeclasses.impl.utils.AutoCloseables.around;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.garciat.typeclasses.impl.ParsedType.Var;
import com.garciat.typeclasses.impl.utils.Either;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.impl.utils.Maybe;
import com.garciat.typeclasses.impl.utils.Sets;
import com.garciat.typeclasses.impl.utils.ZeroOneMore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Resolution {
  private Resolution() {}

  public static <M, V, C, P> Either<Failure<M, V, C, P>, Result<M, V, C, P>> resolve(
      Function<C, List<WitnessConstructor<M, V, C, P>>> constructors, ParsedType<V, C, P> target) {
    return resolveRec(new HashSet<>(), constructors, target);
  }

  private static <M, V, C, P> Either<Failure<M, V, C, P>, Result<M, V, C, P>> resolveRec(
      Set<ParsedType<V, C, P>> seen,
      Function<C, List<WitnessConstructor<M, V, C, P>>> constructors,
      ParsedType<V, C, P> target) {
    if (target instanceof ParsedType.Lazy(var under)) {
      if (seen.contains(under)) {
        return Either.right(new Result.LazyLookup<>(under));
      } else {
        try (var _ = around(() -> seen.add(under), () -> seen.remove(under))) {
          return resolveRec(seen, constructors, under).map(Result.LazyWrap::new);
        }
      }
    }

    var attempts =
        Either.partition(
            Lists.map(
                Witnesses.findWitnesses(constructors, target),
                ctor -> match(seen, constructors, ctor, target)));

    var candidates = OverlappingInstances.reduce(attempts.snd());

    return switch (ZeroOneMore.of(candidates)) {
      case ZeroOneMore.Zero() ->
          Either.left(new Failure.NoMatch<>(target, attempts.fst(), attempts.snd()));
      case ZeroOneMore.More(var matches) -> Either.left(new Failure.Ambiguous<>(target, matches));
      case ZeroOneMore.One(var match) ->
          Either.traverse(match.dependencies(), t -> resolveRec(seen, constructors, t))
              .<Result<M, V, C, P>>map(children -> new Result.Node<>(match, children))
              .mapLeft(f -> new Failure.Nested<>(target, f));
    };
  }

  private static <M, V, C, P> Either<MatchFailure<M, V, C, P>, Match<M, V, C, P>> match(
      Set<ParsedType<V, C, P>> seen,
      Function<C, List<WitnessConstructor<M, V, C, P>>> constructors,
      WitnessConstructor<M, V, C, P> ctor,
      ParsedType<V, C, P> target) {
    return switch (Unification.unify(ctor.returnType(), target)) {
      case Maybe.Nothing() -> Either.left(new MatchFailure.HeadMismatch<>(ctor));
      case Maybe.Just(var returnSubst) -> {
        List<ParsedType<V, C, P>> dependencies =
            Unification.substituteAll(returnSubst, ctor.paramTypes());

        TreeMap<Integer, List<Node<V, C, P>>> nodesByInDegree =
            dependencies.stream()
                .map(Resolution::parseNode)
                .collect(groupingBy(n -> n.in().size(), TreeMap::new, toUnmodifiableList()));

        if (!nodesByInDegree.isEmpty() && nodesByInDegree.firstKey() != 0) {
          // There is a cycle in the dependency graph
          yield Either.left(new MatchFailure.Cycle<>(ctor, dependencies));
        }

        Map<Var<V, C, P>, ParsedType<V, C, P>> substitution = new HashMap<>(returnSubst);

        for (List<Node<V, C, P>> stratum : nodesByInDegree.sequencedValues()) {
          for (Node<V, C, P> node : stratum) {
            {
              var missing = Sets.difference(node.in(), substitution.keySet());
              if (!missing.isEmpty()) {
                // Some input variable has not been satisfied yet
                yield Either.left(new MatchFailure.UnboundVariables<>(ctor, missing));
              }
            }

            switch (flatten(
                resolveRec(
                    seen, constructors, Unification.substitute(substitution, node.type())))) {
              case Either.Right(Result.Node(var possible, _)) -> {
                switch (Unification.unify(
                    Types.unwrapOut1(node.type()), Types.unwrapOut1(possible.witnessType()))) {
                  case Maybe.Just(var childSubst) -> {
                    {
                      var missing = Sets.difference(node.out(), childSubst.keySet());
                      if (!missing.isEmpty()) {
                        // Some output variable has not been satisfied
                        yield Either.left(new MatchFailure.UnproductiveConstraint<>(ctor, missing));
                      }
                    }

                    for (Var<V, C, P> out : childSubst.keySet()) {
                      ParsedType<V, C, P> outT = childSubst.get(out);

                      if (substitution.get(out) instanceof ParsedType<V, C, P> existingT
                          && !existingT.equals(outT)) {
                        // Conflicting substitutions
                        yield Either.left(
                            new MatchFailure.ConflictingSubstitution<>(ctor, out, existingT, outT));
                      }

                      substitution.put(out, outT);
                    }
                  }
                  case Maybe.Nothing() -> {
                    // Child witness does not match expected type
                    yield Either.left(
                        new MatchFailure.ResolvedConstraintMismatch<>(
                            ctor,
                            Types.unwrapOut1(node.type()),
                            Types.unwrapOut1(possible.witnessType())));
                  }
                }
              }
              case Either.Right(Result.LazyWrap(_)) ->
                  throw new IllegalStateException(
                      "flatten should have eliminated LazyWrap cases here");
              case Either.Right(Result.LazyLookup(_)) -> {
                // For now, we just treat them as resolved constraints :shrug:
              }
              case Either.Left(var error) -> {
                // Could not resolve child witness
                yield Either.left(
                    new MatchFailure.UnresolvedConstraint<>(ctor, node.type(), error));
              }
            }
          }
        }

        yield Either.right(
            new Match<>(
                ctor,
                Unification.substituteAll(substitution, ctor.paramTypes()),
                Unification.substitute(substitution, ctor.returnType())));
      }
    };
  }

  private static <V, C, P> Node<V, C, P> parseNode(ParsedType<V, C, P> type) {
    return new Node<>(
        type,
        Types.findOutVars(type).collect(toUnmodifiableSet()),
        Types.findVars(type).collect(toUnmodifiableSet()));
  }

  private static <M, V, C, P> Either<Failure<M, V, C, P>, Result<M, V, C, P>> flatten(
      Either<Failure<M, V, C, P>, Result<M, V, C, P>> result) {
    return switch (result) {
      case Either.Right(Result.LazyWrap(var under)) -> flatten(Either.right(under));
      default -> result;
    };
  }

  public sealed interface Result<M, V, C, P> {
    record Node<M, V, C, P>(Match<M, V, C, P> match, List<Result<M, V, C, P>> children)
        implements Result<M, V, C, P> {}

    record LazyLookup<M, V, C, P>(ParsedType<V, C, P> target) implements Result<M, V, C, P> {}

    record LazyWrap<M, V, C, P>(Result<M, V, C, P> under) implements Result<M, V, C, P> {}
  }

  private record Node<V, C, P>(
      ParsedType<V, C, P> type, Set<Var<V, C, P>> out, Set<Var<V, C, P>> in) {}

  private sealed interface MatchFailure<M, V, C, P> {
    record HeadMismatch<M, V, C, P>(WitnessConstructor<M, V, C, P> ctor)
        implements MatchFailure<M, V, C, P> {}

    record Cycle<M, V, C, P>(WitnessConstructor<M, V, C, P> ctor, List<ParsedType<V, C, P>> types)
        implements MatchFailure<M, V, C, P> {}

    record UnresolvedConstraint<M, V, C, P>(
        WitnessConstructor<M, V, C, P> ctor,
        ParsedType<V, C, P> constraint,
        Failure<M, V, C, P> cause)
        implements MatchFailure<M, V, C, P> {}

    record ResolvedConstraintMismatch<M, V, C, P>(
        WitnessConstructor<M, V, C, P> ctor,
        ParsedType<V, C, P> expected,
        ParsedType<V, C, P> actual)
        implements MatchFailure<M, V, C, P> {}

    record UnboundVariables<M, V, C, P>(
        WitnessConstructor<M, V, C, P> ctor, Set<Var<V, C, P>> variables)
        implements MatchFailure<M, V, C, P> {}

    record UnproductiveConstraint<M, V, C, P>(
        WitnessConstructor<M, V, C, P> ctor, Set<Var<V, C, P>> variables)
        implements MatchFailure<M, V, C, P> {}

    record ConflictingSubstitution<M, V, C, P>(
        WitnessConstructor<M, V, C, P> ctor,
        Var<V, C, P> variable,
        ParsedType<V, C, P> existing,
        ParsedType<V, C, P> conflicting)
        implements MatchFailure<M, V, C, P> {}

    default String format() {
      return switch (this) {
        case HeadMismatch(var ctor) ->
            "Witness constructor " + ctor.format() + " does not match the target type.";
        case Cycle(var ctor, var types) ->
            "Witness constructor "
                + ctor.format()
                + " has cyclic dependencies: "
                + types.stream().map(ParsedType::format).collect(Collectors.joining(", "));
        case UnresolvedConstraint(var ctor, var constraint, var cause) ->
            "Could not resolve constraint "
                + constraint.format()
                + " for witness constructor "
                + ctor.format()
                + ":\nCaused by: "
                + cause.format().indent(2);
        case ResolvedConstraintMismatch(var ctor, var expected, var actual) ->
            "Resolved constraint for witness constructor "
                + ctor.format()
                + " does not match expected type: expected "
                + expected.format()
                + ", got "
                + actual.format()
                + ".";
        case UnboundVariables(var ctor, var variables) ->
            "Witness constructor "
                + ctor.format()
                + " has unbound input variables: "
                + variables.stream().map(Var::format).collect(Collectors.joining(", "));
        case UnproductiveConstraint(var ctor, var variables) ->
            "Witness constructor "
                + ctor.format()
                + " has unproductive output variables: "
                + variables.stream().map(Var::format).collect(Collectors.joining(", "));
        case ConflictingSubstitution(var ctor, var variable, var existing, var conflicting) ->
            "Witness constructor "
                + ctor.format()
                + " has conflicting substitutions for variable "
                + variable.format()
                + ": existing substitution "
                + existing.format()
                + ", conflicting substitution "
                + conflicting.format()
                + ".";
      };
    }
  }

  public sealed interface Failure<M, V, C, P> {
    record NoMatch<M, V, C, P>(
        ParsedType<V, C, P> target,
        List<MatchFailure<M, V, C, P>> failures,
        List<Match<M, V, C, P>> matches)
        implements Failure<M, V, C, P> {}

    record Ambiguous<M, V, C, P>(ParsedType<V, C, P> target, List<Match<M, V, C, P>> candidates)
        implements Failure<M, V, C, P> {}

    record Nested<M, V, C, P>(ParsedType<V, C, P> target, Failure<M, V, C, P> cause)
        implements Failure<M, V, C, P> {}

    default String format() {
      return switch (this) {
        case NoMatch(var target, var failures, var matches) ->
            "No witnesses found for type: "
                + target.format()
                + "\nFailures:\n"
                + failures.stream()
                    .map(MatchFailure::format)
                    .collect(Collectors.joining("\n"))
                    .indent(2)
                + (matches.isEmpty()
                    ? ""
                    : "\nPartial matches:\n"
                        + matches.stream()
                            .map(Match::ctor)
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
