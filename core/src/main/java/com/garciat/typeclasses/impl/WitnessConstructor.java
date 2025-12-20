package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.api.TypeClass;
import java.util.List;

public interface WitnessConstructor<V, C, P> {
  TypeClass.Witness.Overlap overlap();

  List<ParsedType<V, C, P>> paramTypes();

  ParsedType<V, C, P> returnType();
}
