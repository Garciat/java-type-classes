package com.garciat.typeclasses.impl;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public record FuncType(Method java, List<ParsedType> paramTypes, ParsedType returnType) {
  public String format() {
    return String.format(
        "%s%s -> %s",
        Arrays.stream(java.getTypeParameters())
            .map(TypeVariable::getName)
            .reduce((a, b) -> a + " " + b)
            .map("∀ %s. "::formatted)
            .orElse(""),
        paramTypes.stream().map(ParsedType::format).collect(Collectors.joining(", ", "(", ")")),
        returnType.format());
  }

  public static FuncType parse(Method method) {
    if (!Modifier.isStatic(method.getModifiers())) {
      throw new IllegalArgumentException("Method must be static: " + method);
    }
    return new FuncType(
        method,
        ParsedType.parseAll(method.getGenericParameterTypes()),
        ParsedType.parse(method.getGenericReturnType()));
  }
}
