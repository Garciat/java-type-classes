package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.utils.ZeroOneMore;
import com.garciat.typeclasses.types.Either;
import com.garciat.typeclasses.types.Maybe;
import java.util.List;
import java.util.stream.Collectors;

public final class WitnessResolution {
  private WitnessResolution() {}

  public static Either<ResolutionError, InstantiationPlan> resolve(
      RuntimeWitnessSystem system, ParsedType target) {
    List<Match> matches =
        OverlappingInstances.reduce(system.findRules(target)).stream()
            .flatMap(rule -> tryMatch(rule, target).stream())
            .toList();

    return switch (ZeroOneMore.of(matches)) {
      case ZeroOneMore.One(Match(var rule, var requirements)) ->
          Either.traverse(requirements, r -> resolve(system, r))
              .<InstantiationPlan>map(dependencies -> new InstantiationPlan(rule, dependencies))
              .mapLeft(error -> new ResolutionError.Nested(target, error));
      case ZeroOneMore.Zero() -> Either.left(new ResolutionError.NotFound(target));
      case ZeroOneMore.More(var ambiguousMatches) ->
          Either.left(
              new ResolutionError.Ambiguous(
                  target, ambiguousMatches.stream().map(Match::rule).toList()));
    };
  }

  private static Maybe<Match> tryMatch(WitnessConstructor rule, ParsedType target) {
    return Unification.unify(rule.returnType(), target)
        .map(map -> Unification.substituteAll(map, rule.paramTypes()))
        .map(requirements -> new Match(rule, requirements));
  }

  private record Match(WitnessConstructor rule, List<ParsedType> requirements) {}

  public record InstantiationPlan(
      WitnessConstructor target, List<InstantiationPlan> dependencies) {}

  public sealed interface ResolutionError {
    record NotFound(ParsedType target) implements ResolutionError {}

    record Ambiguous(ParsedType target, List<WitnessConstructor> candidates)
        implements ResolutionError {}

    record Nested(ParsedType target, ResolutionError cause) implements ResolutionError {}

    default String format() {
      return switch (this) {
        case NotFound(ParsedType target) -> "No witness found for type: " + target.format();
        case Ambiguous(ParsedType target, List<WitnessConstructor> candidates) ->
            "Ambiguous witnesses found for type: "
                + target.format()
                + "\nCandidates:\n"
                + candidates.stream()
                    .map(WitnessConstructor::format)
                    .collect(Collectors.joining("\n"))
                    .indent(2);
        case Nested(ParsedType target, ResolutionError cause) ->
            "While resolving witness for type: "
                + target.format()
                + "\nCaused by: "
                + cause.format().indent(2);
      };
    }
  }
}
