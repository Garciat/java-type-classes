package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.ParsedType.Var;
import com.garciat.typeclasses.impl.utils.Either;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.impl.utils.Maybe;
import com.garciat.typeclasses.impl.utils.ZeroOneMore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

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
      if (seen.contains(under)) {
        return Either.right(new Result.LazyLookup<>(under));
      } else {
        seen.add(under);
        var out =
            resolveRec(seen, constructors, under).<Result<M, V, C, P>>map(Result.LazyWrap::new);
        seen.remove(under);
        return out;
      }
    }

    var witnesses = findWitnesses(constructors, target).stream().distinct().toList();

    var candidates =
        OverlappingInstances.reduce(
            Maybe.mapMaybe(witnesses, ctor -> match(seen, constructors, ctor, target)));

    return switch (ZeroOneMore.of(candidates)) {
      case ZeroOneMore.Zero() -> Either.left(new Failure.NoMatch<>(target, witnesses));
      case ZeroOneMore.More(var matches) -> Either.left(new Failure.Ambiguous<>(target, matches));
      case ZeroOneMore.One(var match) ->
          Either.traverse(match.dependencies(), t -> resolveRec(seen, constructors, t))
              .<Result<M, V, C, P>>map(children -> new Result.Node<>(match, children))
              .mapLeft(f -> new Failure.Nested<>(target, f));
    };
  }

  private static <M, V, C, P> Maybe<Match<M, V, C, P>> match(
      Set<ParsedType<V, C, P>> seen,
      Function<C, List<WitnessConstructor<M, V, C, P>>> constructors,
      WitnessConstructor<M, V, C, P> ctor,
      ParsedType<V, C, P> target) {
    return switch (Unification.unify(ctor.returnType(), target)) {
      case Maybe.Nothing() -> Maybe.nothing();
      case Maybe.Just(var returnSubst) -> {
        TreeMap<Integer, List<Node<V, C, P>>> nodesByInDegree =
            Unification.substituteAll(returnSubst, ctor.paramTypes()).stream()
                .map(Resolution::parseNode)
                .collect(groupingBy(n -> n.in().size(), TreeMap::new, toUnmodifiableList()));

        if (!nodesByInDegree.isEmpty() && nodesByInDegree.firstKey() != 0) {
          // There is a cycle in the dependencies
          yield Maybe.nothing();
        }

        Map<Var<V, C, P>, ParsedType<V, C, P>> substitution = new HashMap<>(returnSubst);

        for (List<Node<V, C, P>> stratum : nodesByInDegree.sequencedValues()) {
          for (Node<V, C, P> node : stratum) {
            if (!substitution.keySet().containsAll(node.in())) {
              // Some input variable has not been satisfied yet
              yield Maybe.nothing();
            }

            switch (resolveRec(
                seen, constructors, Unification.substitute(substitution, node.type()))) {
              case Either.Right(Result.Node(var possible, _)) -> {
                switch (Unification.unify(
                    Types.unwrapOut1(node.type()), Types.unwrapOut1(possible.witnessType()))) {
                  case Maybe.Just(var childSubst) -> {
                    for (Var<V, C, P> out : node.out()) {
                      ParsedType<V, C, P> outT = childSubst.get(out);
                      if (outT == null) {
                        // Some output variable has not been satisfied
                        yield Maybe.nothing();
                      }
                      if (substitution.get(out) instanceof ParsedType<V, C, P> t
                          && !t.equals(outT)) {
                        // Conflicting substitutions
                        yield Maybe.nothing();
                      }
                      substitution.put(out, outT);
                    }
                  }
                  case Maybe.Nothing() -> {
                    // Child witness does not match expected type
                    yield Maybe.nothing();
                  }
                }
              }
              default -> {
                // Could not resolve child witness
                yield Maybe.nothing();
              }
            }
          }
        }

        yield Maybe.just(
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
        Types.findOuts(type)
            .flatMap(
                t ->
                    switch (t) {
                      case ParsedType.Out(Var<V, C, P> v) -> Stream.of(v);
                      default -> Stream.of();
                    })
            .collect(toUnmodifiableSet()),
        Types.findVars(type).collect(toUnmodifiableSet()));
  }

  private record Node<V, C, P>(
      ParsedType<V, C, P> type, Set<Var<V, C, P>> out, Set<Var<V, C, P>> in) {}

  private static <M, V, C, P> List<WitnessConstructor<M, V, C, P>> findWitnesses(
      Function<C, List<WitnessConstructor<M, V, C, P>>> constructors, ParsedType<V, C, P> target) {
    return switch (target) {
      case ParsedType.App(var fun1, ParsedType.App(var fun2, _)) ->
          Lists.concat(findWitnesses(constructors, fun1), findWitnesses(constructors, fun2));
      case ParsedType.App(var fun, var arg) ->
          Lists.concat(findWitnesses(constructors, fun), findWitnesses(constructors, arg));
      case ParsedType.Const<V, C, P> c -> constructors.apply(c.repr());
      case ParsedType.Lazy(var under) -> findWitnesses(constructors, under);
      case Var(_),
          ParsedType.Out(_),
          ParsedType.ArrayOf(_),
          ParsedType.Primitive(_),
          ParsedType.Wildcard() ->
          List.of();
    };
  }

  public sealed interface Failure<M, V, C, P> {
    record NoMatch<M, V, C, P>(
        ParsedType<V, C, P> target, List<WitnessConstructor<M, V, C, P>> witnesses)
        implements Failure<M, V, C, P> {}

    record Ambiguous<M, V, C, P>(ParsedType<V, C, P> target, List<Match<M, V, C, P>> candidates)
        implements Failure<M, V, C, P> {}

    record Nested<M, V, C, P>(ParsedType<V, C, P> target, Failure<M, V, C, P> cause)
        implements Failure<M, V, C, P> {}

    default String format() {
      return switch (this) {
        case NoMatch(var target, var candidates) ->
            "No matching witnesses found for type: "
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
