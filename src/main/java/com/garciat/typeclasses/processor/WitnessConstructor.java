package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.api.TypeClass;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeParameterElement;

public record WitnessConstructor(
    ExecutableElement method,
    TypeClass.Witness.Overlap overlap,
    List<ParsedType> paramTypes,
    ParsedType returnType) {
  public static String format(WitnessConstructor constructor) {
    return String.format(
        "%s%s -> %s",
        constructor.method().getTypeParameters().stream()
            .map(TypeParameterElement::getSimpleName)
            .map(Name::toString)
            .reduce((a, b) -> a + " " + b)
            .map("∀ %s. "::formatted)
            .orElse(""),
        constructor.paramTypes().stream()
            .map(ParsedType::format)
            .collect(Collectors.joining(", ", "(", ")")),
        ParsedType.format(constructor.returnType()));
  }
}
