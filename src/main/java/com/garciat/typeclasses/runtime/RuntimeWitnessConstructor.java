package com.garciat.typeclasses.runtime;

import static com.garciat.typeclasses.api.TypeClass.Witness;

import com.garciat.typeclasses.impl.ParsedType;
import com.garciat.typeclasses.impl.WitnessConstructor;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public record RuntimeWitnessConstructor(
    Method java,
    Witness.Overlap overlap,
    List<ParsedType<TypeVariable<?>, Class<?>, Class<?>>> paramTypes,
    ParsedType<TypeVariable<?>, Class<?>, Class<?>> returnType)
    implements WitnessConstructor<TypeVariable<?>, Class<?>, Class<?>> {
  public static String format(RuntimeWitnessConstructor wc) {
    return String.format(
        "%s%s -> %s",
        Arrays.stream(wc.java().getTypeParameters())
            .map(TypeVariable::getName)
            .reduce((a, b) -> a + " " + b)
            .map("∀ %s. "::formatted)
            .orElse(""),
        wc.paramTypes().stream()
            .map(RuntimeWitnessSystem::format)
            .collect(Collectors.joining(", ", "(", ")")),
        RuntimeWitnessSystem.format(wc.returnType()));
  }
}
