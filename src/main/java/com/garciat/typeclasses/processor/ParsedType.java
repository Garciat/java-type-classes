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

  static String format(ParsedType ty) {
    return switch (ty) {
      case Var v -> v.java.toString();
      case Const c ->
          c.java().getSimpleName()
              + c.java().getTypeParameters().stream()
                  .map(TypeParameterElement::toString)
                  .reduce((a, b) -> a + ", " + b)
                  .map(s -> "[" + s + "]")
                  .orElse("");
      case App a -> ParsedType.format(a.fun()) + "(" + ParsedType.format(a.arg()) + ")";
      case ArrayOf a -> ParsedType.format(a.elementType()) + "[]";
      case Primitive p -> p.java().toString();
    };
  }
}
