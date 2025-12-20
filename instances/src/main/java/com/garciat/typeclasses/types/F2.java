package com.garciat.typeclasses.types;

import java.util.function.Function;

@FunctionalInterface
public interface F2<A, B, R> {
  R apply(A a, B b);

  static <A, B, R> F2<A, B, R> of(Function<A, Function<B, R>> f) {
    return (a, b) -> f.apply(a).apply(b);
  }
}
