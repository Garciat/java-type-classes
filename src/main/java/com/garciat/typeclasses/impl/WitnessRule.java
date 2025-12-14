package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.types.Maybe;
import java.util.List;

public sealed interface WitnessRule {
  Maybe<List<ParsedType>> tryMatch(ParsedType target);

  record ContextInstance(Object instance, ParsedType type) implements WitnessRule {
    @Override
    public Maybe<List<ParsedType>> tryMatch(ParsedType target) {
      return target.equals(type) ? Maybe.just(List.of()) : Maybe.nothing();
    }

    @Override
    public String toString() {
      return "context instance: " + type.format();
    }
  }

  record InstanceConstructor(FuncType func) implements WitnessRule {
    public TypeClass.Witness.Overlap overlap() {
      return func.java().getAnnotation(TypeClass.Witness.class).overlap();
    }

    @Override
    public Maybe<List<ParsedType>> tryMatch(ParsedType target) {
      return Unification.unify(func.returnType(), target)
          .map(map -> Unification.substituteAll(map, func.paramTypes()));
    }

    @Override
    public String toString() {
      return func.format();
    }
  }
}
