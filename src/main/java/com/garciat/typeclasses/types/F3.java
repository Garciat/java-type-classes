package com.garciat.typeclasses.types;

import java.util.function.Function;

@FunctionalInterface
public interface F3<A, B, C, R> {
  R apply(A a, B b, C c);

  static <A, B, C, R> F3<A, B, C, R> of(Function<A, Function<B, Function<C, R>>> f) {
    return (a, b, c) -> f.apply(a).apply(b).apply(c);
  }
}
