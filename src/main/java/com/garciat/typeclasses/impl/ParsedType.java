package com.garciat.typeclasses.impl;

import java.lang.reflect.TypeVariable;
import java.util.Arrays;

public sealed interface ParsedType {
  record Var(TypeVariable<?> java) implements ParsedType {}

  record App(ParsedType fun, ParsedType arg) implements ParsedType {}

  record ArrayOf(ParsedType elementType) implements ParsedType {}

  record Const(Class<?> java) implements ParsedType {}

  record Primitive(Class<?> java) implements ParsedType {}

  static String format(ParsedType ty) {
    return switch (ty) {
      case Var v -> v.java().getName();
      case Const c ->
          c.java().getSimpleName()
              + Arrays.stream(c.java().getTypeParameters())
                  .map(TypeVariable::getName)
                  .reduce((a, b) -> a + ", " + b)
                  .map(s -> "[" + s + "]")
                  .orElse("");
      case App a -> ParsedType.format(a.fun()) + "(" + ParsedType.format(a.arg()) + ")";
      case ArrayOf a -> ParsedType.format(a.elementType()) + "[]";
      case Primitive p -> p.java().getSimpleName();
    };
  }
}
