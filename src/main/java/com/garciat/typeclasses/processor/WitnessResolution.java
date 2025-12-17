package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.impl.utils.ZeroOneMore;
import com.garciat.typeclasses.types.Either;
import com.garciat.typeclasses.types.Maybe;
import java.util.List;
import java.util.stream.Collectors;

public final class WitnessResolution {
  private WitnessResolution() {}

  /** Resolves a ParsedType into an InstantiationPlan. */
  public static Either<ResolutionError, InstantiationPlan> resolve(
      StaticWitnessSystem system, ParsedType target) {
    List<Match> matches =
        OverlappingInstances.reduce(system.findRules(target)).stream()
            .flatMap(rule -> tryMatch(rule, target).stream())
            .toList();

    return switch (ZeroOneMore.of(matches)) {
      case ZeroOneMore.One(Match(var rule, var requirements)) ->
          Either.traverse(requirements, req -> resolve(system, req))
              .<InstantiationPlan>map(
                  dependencies -> new InstantiationPlan.PlanStep(rule, dependencies))
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

  record Match(WitnessConstructor rule, List<ParsedType> requirements) {}

  /**
   * Represents the fully resolved instantiation plan. This is a tree structure where each node is a
   * step in the instantiation process, with dependencies on other steps.
   */
  public sealed interface InstantiationPlan {
    record PlanStep(WitnessConstructor target, List<InstantiationPlan> dependencies)
        implements InstantiationPlan {}
  }

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
                    .map(WitnessConstructor::toString)
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
