package com.garciat.typeclasses.impl;

import static com.garciat.typeclasses.impl.utils.AutoCloseables.around;
import static com.garciat.typeclasses.impl.utils.Unit.unit;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.garciat.typeclasses.impl.ParsedType.Var;
import com.garciat.typeclasses.impl.utils.Either;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.impl.utils.Maybe;
import com.garciat.typeclasses.impl.utils.Pair;
import com.garciat.typeclasses.impl.utils.Unit;
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
    // Lazy and cycle detection
    {
      if (seen.contains(target)) {
        if (target instanceof ParsedType.Lazy(var under)) {
          return Either.right(new Result.LazyLookup<>(under));
        } else {
          return Either.left(new Failure.ResolutionCycle<>(target));
        }
      }

      if (target instanceof ParsedType.Lazy(var under)) {
        try (var _ = around(() -> seen.add(target), () -> seen.remove(target))) {
          return resolveRec(seen, constructors, under).map(Result.LazyWrap::new);
        }
      }
    }

    // Free variable check
    {
      Set<Var<V, C, P>> freeVars = Types.findVars(target).collect(toUnmodifiableSet());
      if (!freeVars.isEmpty()) {
        return Either.left(new Failure.FreeVariables<>(target, freeVars));
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
      case Maybe.Just(var headSubst) -> {
        List<ParsedType<V, C, P>> dependencies =
            Unification.substituteAll(headSubst, ctor.paramTypes());

        List<ParsedType<V, C, P>> dependenciesByTopo;

        switch (sortByTopo(dependencies)) {
          case Either.Right(var sorted) -> dependenciesByTopo = sorted;
          case Either.Left(TopoFailure.Conflict(var typeA, var typeB)) -> {
            yield Either.left(new MatchFailure.ConflictingConstraints<>(ctor, typeA, typeB));
          }
          case Either.Left(TopoFailure.Cycle()) -> {
            yield Either.left(new MatchFailure.Cycle<>(ctor, dependencies));
          }
        }

        Map<Var<V, C, P>, ParsedType<V, C, P>> substitution = new HashMap<>(headSubst);

        for (ParsedType<V, C, P> dependency : dependenciesByTopo) {
          switch (flatten(
              resolveRec(seen, constructors, Unification.substitute(substitution, dependency)))) {
            case Either.Right(Result.Node(var resolved, _)) -> {
              switch (Unification.unify(
                  Types.unwrapOut1(dependency), Types.unwrapOut1(resolved.witnessType()))) {
                case Maybe.Just(var childSubst) -> substitution.putAll(childSubst);
                case Maybe.Nothing() -> {
                  // Child witness does not match expected type
                  yield Either.left(
                      new MatchFailure.ResolvedConstraintMismatch<>(
                          ctor,
                          Types.unwrapOut1(dependency),
                          Types.unwrapOut1(resolved.witnessType())));
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
              yield Either.left(new MatchFailure.UnresolvedConstraint<>(ctor, dependency, error));
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

  private static <V, C, P> Either<TopoFailure<V, C, P>, List<ParsedType<V, C, P>>> sortByTopo(
      List<ParsedType<V, C, P>> dependencies) {
    List<Node<V, C, P>> nodes = Lists.map(dependencies, Resolution::parseNode);

    switch (checkDisjoint(nodes)) {
      case Either.Left(Pair(var c1, var c2)) -> {
        return Either.left(new TopoFailure.Conflict<>(c1, c2));
      }
      case Either.Right(Unit()) -> {}
    }

    TreeMap<Integer, List<Node<V, C, P>>> nodesByInDegree =
        nodes.stream().collect(groupingBy(n -> n.in().size(), TreeMap::new, toUnmodifiableList()));

    if (!nodesByInDegree.isEmpty() && nodesByInDegree.firstKey() != 0) {
      return Either.left(new TopoFailure.Cycle<>());
    }

    List<ParsedType<V, C, P>> dependenciesByTopo =
        nodesByInDegree.sequencedValues().stream().flatMap(List::stream).map(Node::type).toList();

    return Either.right(dependenciesByTopo);
  }

  private sealed interface TopoFailure<V, C, P> {
    record Conflict<V, C, P>(ParsedType<V, C, P> typeA, ParsedType<V, C, P> typeB)
        implements TopoFailure<V, C, P> {}

    record Cycle<V, C, P>() implements TopoFailure<V, C, P> {}
  }

  private static <V, C, P> Node<V, C, P> parseNode(ParsedType<V, C, P> type) {
    return new Node<>(
        type,
        Types.findOutVars(type).collect(toUnmodifiableSet()),
        Types.findVars(type).collect(toUnmodifiableSet()));
  }

  private static <V, C, P>
      Either<Pair<ParsedType<V, C, P>, ParsedType<V, C, P>>, Unit> checkDisjoint(
          List<Node<V, C, P>> nodes) {
    Map<Var<V, C, P>, ParsedType<V, C, P>> seen = new HashMap<>();

    for (Node<V, C, P> node : nodes) {
      for (Var<V, C, P> out : node.out()) {
        if (seen.put(out, node.type()) instanceof ParsedType<V, C, P> existing) {
          return Either.left(new Pair<>(existing, node.type()));
        }
      }
    }

    return Either.right(unit());
  }

  private static <M, V, C, P> Either<Failure<M, V, C, P>, Result<M, V, C, P>> flatten(
      Either<Failure<M, V, C, P>, Result<M, V, C, P>> result) {
    return switch (result) {
      case Either.Right(Result.LazyWrap(var under)) -> flatten(Either.right(under));
      default -> result;
    };
  }

  private record Node<V, C, P>(
      ParsedType<V, C, P> type, Set<Var<V, C, P>> out, Set<Var<V, C, P>> in) {}

  public sealed interface MatchFailure<M, V, C, P> {
    record HeadMismatch<M, V, C, P>(WitnessConstructor<M, V, C, P> ctor)
        implements MatchFailure<M, V, C, P> {}

    record ConflictingConstraints<M, V, C, P>(
        WitnessConstructor<M, V, C, P> ctor,
        ParsedType<V, C, P> constraintA,
        ParsedType<V, C, P> constraintB)
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

    default String format() {
      return switch (this) {
        case HeadMismatch(var ctor) ->
            "Witness constructor " + ctor.format() + " does not match the target type.";
        case ConflictingConstraints(var ctor, var constraintA, var constraintB) ->
            "Witness constructor "
                + ctor.format()
                + " has out-var conflicting constraints: "
                + constraintA.format()
                + " and "
                + constraintB.format()
                + ".";
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
      };
    }
  }

  public sealed interface Failure<M, V, C, P> {
    record ResolutionCycle<M, V, C, P>(ParsedType<V, C, P> target) implements Failure<M, V, C, P> {}

    record FreeVariables<M, V, C, P>(ParsedType<V, C, P> target, Set<Var<V, C, P>> variables)
        implements Failure<M, V, C, P> {}

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
        case ResolutionCycle(var target) ->
            "Resolution cycle detected while resolving witness for type: " + target.format();
        case FreeVariables(var target, var variables) ->
            "Cannot resolve witness for type: "
                + target.format()
                + "\nFree variables: "
                + variables.stream().map(Var::format).collect(Collectors.joining(", "));
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
