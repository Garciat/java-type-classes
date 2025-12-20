package com.garciat.typeclasses.impl;

import static com.garciat.typeclasses.api.TypeClass.Witness;

import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public record WitnessConstructor(
    Method java, Witness.Overlap overlap, List<ParsedType> paramTypes, ParsedType returnType) {
  public static String format(WitnessConstructor wc) {
    return String.format(
        "%s%s -> %s",
        Arrays.stream(wc.java().getTypeParameters())
            .map(TypeVariable::getName)
            .reduce((a, b) -> a + " " + b)
            .map("∀ %s. "::formatted)
            .orElse(""),
        wc.paramTypes().stream()
            .map(ParsedType::format)
            .collect(Collectors.joining(", ", "(", ")")),
        ParsedType.format(wc.returnType()));
  }
}
