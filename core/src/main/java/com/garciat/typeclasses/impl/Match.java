package com.garciat.typeclasses.impl;

import java.util.List;

public record Match<M, V, C, P>(
    WitnessConstructor<M, V, C, P> ctor,
    List<ParsedType<V, C, P>> dependencies,
    ParsedType<V, C, P> witnessType) {}
