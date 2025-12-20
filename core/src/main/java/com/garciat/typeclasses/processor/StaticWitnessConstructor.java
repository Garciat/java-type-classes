package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.impl.ParsedType;
import com.garciat.typeclasses.impl.WitnessConstructor;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeVariable;

public record StaticWitnessConstructor(
    ExecutableElement method,
    TypeClass.Witness.Overlap overlap,
    List<ParsedType<TypeVariable, TypeElement, PrimitiveType>> paramTypes,
    ParsedType<TypeVariable, TypeElement, PrimitiveType> returnType)
    implements WitnessConstructor<TypeVariable, TypeElement, PrimitiveType> {}
