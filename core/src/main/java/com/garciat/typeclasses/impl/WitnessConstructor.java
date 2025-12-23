package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.api.TypeClass;
import java.util.List;
import java.util.stream.Collectors;

public record WitnessConstructor<M, V, C, P>(
    M method,
    TypeClass.Witness.Overlap overlap,
    List<ParsedType.Var<V, C, P>> typeParams,
    List<ParsedType<V, C, P>> paramTypes,
    ParsedType<V, C, P> returnType) {
  public String format() {
    return String.format(
        "%s%s -> %s",
        typeParams().stream()
            .map(ParsedType::format)
            .reduce((a, b) -> a + " " + b)
            .map("∀ %s. "::formatted)
            .orElse(""),
        paramTypes().stream().map(ParsedType::format).collect(Collectors.joining(", ", "(", ")")),
        returnType().format());
  }
}
