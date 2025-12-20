package com.garciat.typeclasses.types;

import java.util.function.Function;

@FunctionalInterface
public interface F1<A, R> {
  R apply(A a);

  static <A, R> F1<A, R> of(Function<A, R> f) {
    return f::apply;
  }
}
