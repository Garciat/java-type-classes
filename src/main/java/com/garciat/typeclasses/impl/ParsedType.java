package com.garciat.typeclasses.impl;

import java.lang.reflect.TypeVariable;
import java.util.Arrays;

public sealed interface ParsedType {
  record Var(TypeVariable<?> java) implements ParsedType {}

  record App(ParsedType fun, ParsedType arg) implements ParsedType {}

  record ArrayOf(ParsedType elementType) implements ParsedType {}

  record Const(Class<?> java) implements ParsedType {}

  record Primitive(Class<?> java) implements ParsedType {}

  default String format() {
    return switch (this) {
      case Var v -> v.java.getName();
      case Const c ->
          c.java().getSimpleName()
              + Arrays.stream(c.java().getTypeParameters())
                  .map(TypeVariable::getName)
                  .reduce((a, b) -> a + ", " + b)
                  .map(s -> "[" + s + "]")
                  .orElse("");
      case App a -> a.fun.format() + "(" + a.arg.format() + ")";
      case ArrayOf a -> a.elementType.format() + "[]";
      case Primitive p -> p.java().getSimpleName();
    };
  }
}
