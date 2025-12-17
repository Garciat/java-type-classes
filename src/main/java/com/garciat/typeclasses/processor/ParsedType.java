package com.garciat.typeclasses.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeVariable;

public sealed interface ParsedType {
  record Var(TypeVariable java) implements ParsedType {}

  record App(ParsedType fun, ParsedType arg) implements ParsedType {}

  record ArrayOf(ParsedType elementType) implements ParsedType {}

  record Const(TypeElement java) implements ParsedType {}

  record Primitive(PrimitiveType java) implements ParsedType {}

  default String format() {
    return switch (this) {
      case Var v -> v.java.toString();
      case Const c ->
          c.java().getSimpleName()
              + c.java().getTypeParameters().stream()
                  .map(TypeParameterElement::toString)
                  .reduce((a, b) -> a + ", " + b)
                  .map(s -> "[" + s + "]")
                  .orElse("");
      case App a -> a.fun.format() + "(" + a.arg.format() + ")";
      case ArrayOf a -> a.elementType.format() + "[]";
      case Primitive p -> p.java().toString();
    };
  }
}
