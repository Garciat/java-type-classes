package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.api.TypeClass;
import java.util.List;
import java.util.stream.Collectors;

public record WitnessConstructor<M, V, C, P>(
    M method,
    TypeClass.Witness.Overlap overlap,
    List<ParsedType.TyParam<V>> typeParams,
    List<ParsedType<V, C, P>> paramTypes,
    ParsedType<V, C, P> returnType) {
  public String format() {
    return String.format(
        "%s = %s%s -> %s",
        method.toString(),
        typeParams().stream()
            .map(ParsedType.TyParam::toString)
            .reduce((a, b) -> a + " " + b)
            .map("∀ %s. "::formatted)
            .orElse(""),
        paramTypes().stream().map(ParsedType::format).collect(Collectors.joining(", ", "(", ")")),
        returnType().format());
  }
}
