package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.api.TypeClass;
import java.util.List;
import javax.lang.model.element.ExecutableElement;

public record WitnessConstructor(
    ExecutableElement method,
    TypeClass.Witness.Overlap overlap,
    List<ParsedType> paramTypes,
    ParsedType returnType) {}
