package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.impl.Resolution;
import com.garciat.typeclasses.impl.utils.Rose;
import com.garciat.typeclasses.types.Either;
import java.util.List;
import java.util.stream.Collectors;

public final class WitnessResolution {
  private WitnessResolution() {}

  public static Either<Resolution.Failure<ParsedType, WitnessConstructor>, Rose<WitnessConstructor>>
      resolve(StaticWitnessSystem system, ParsedType target) {
    return Resolution.resolve(
        t -> OverlappingInstances.reduce(system.findRules(t)),
        (t, c) ->
            Unification.unify(c.returnType(), t)
                .map(map -> Unification.substituteAll(map, c.paramTypes())),
        target);
  }

  public static String format(Resolution.Failure<ParsedType, WitnessConstructor> error) {
    return switch (error) {
      case Resolution.NotFound(ParsedType target) ->
          "No witness found for type: " + target.format();
      case Resolution.Ambiguous(ParsedType target, List<WitnessConstructor> candidates) ->
          "Ambiguous witnesses found for type: "
              + target.format()
              + "\nCandidates:\n"
              + candidates.stream()
                  .map(WitnessConstructor::toString)
                  .collect(Collectors.joining("\n"))
                  .indent(2);
      case Resolution.Nested(
              ParsedType target,
              Resolution.Failure<ParsedType, WitnessConstructor> cause) ->
          "While resolving witness for type: "
              + target.format()
              + "\nCaused by: "
              + format(cause).indent(2);
    };
  }
}
