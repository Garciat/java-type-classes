package com.garciat.typeclasses.impl;

public sealed interface ParsedType<V, C, P> {
  record Var<V, C, P>(V java) implements ParsedType<V, C, P> {}

  record App<V, C, P>(ParsedType<V, C, P> fun, ParsedType<V, C, P> arg)
      implements ParsedType<V, C, P> {}

  record ArrayOf<V, C, P>(ParsedType<V, C, P> elementType) implements ParsedType<V, C, P> {}

  record Const<V, C, P>(C java) implements ParsedType<V, C, P> {}

  record Primitive<V, C, P>(P java) implements ParsedType<V, C, P> {}
}
