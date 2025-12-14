package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.WitnessRule.ContextInstance;
import com.garciat.typeclasses.impl.utils.ZeroOneMore;
import com.garciat.typeclasses.types.Either;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class WitnessResolution {
  private WitnessResolution() {}

  /** Resolves a ParsedType into an InstantiationPlan. */
  public static Either<ResolutionError, InstantiationPlan> resolve(
      ParsedType target, List<ContextInstance> context) {
    record Match(WitnessRule rule, List<ParsedType> requirements) {}

    List<Match> matches =
        Stream.<WitnessRule>concat(
                context.stream(),
                OverlappingInstances.reduce(ConstructorLookup.findRules(target)).stream())
            .flatMap(
                rule ->
                    rule
                        .tryMatch(target)
                        .map(requirements -> new Match(rule, requirements))
                        .stream())
            .toList();

    return switch (ZeroOneMore.of(matches)) {
      case ZeroOneMore.One<Match>(Match(var rule, var requirements)) ->
          Either.traverse(requirements, req -> resolve(req, context))
              .<InstantiationPlan>map(
                  dependencies -> new InstantiationPlan.PlanStep(rule, dependencies))
              .mapLeft(error -> new ResolutionError.Nested(target, error));
      case ZeroOneMore.Zero<Match>() -> Either.left(new ResolutionError.NotFound(target));
      case ZeroOneMore.More<Match>(var matches2) ->
          Either.left(
              new ResolutionError.Ambiguous(target, matches2.stream().map(Match::rule).toList()));
    };
  }

  /**
   * Represents the fully resolved instantiation plan. This is a tree structure where each node is a
   * step in the instantiation process, with dependencies on other steps.
   */
  public sealed interface InstantiationPlan {
    record PlanStep(WitnessRule target, List<InstantiationPlan> dependencies)
        implements InstantiationPlan {}
  }

  public sealed interface ResolutionError {
    record NotFound(ParsedType target) implements ResolutionError {}

    record Ambiguous(ParsedType target, List<WitnessRule> candidates) implements ResolutionError {}

    record Nested(ParsedType target, ResolutionError cause) implements ResolutionError {}

    default String format() {
      return switch (this) {
        case NotFound(ParsedType target) -> "No witness found for type: " + target.format();
        case Ambiguous(ParsedType target, List<WitnessRule> candidates) ->
            "Ambiguous witnesses found for type: "
                + target.format()
                + "\nCandidates:\n"
                + candidates.stream()
                    .map(WitnessRule::toString)
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
