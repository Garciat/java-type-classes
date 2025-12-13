package com.garciat.typeclasses.impl;

import java.util.function.BiFunction;
import java.util.function.Function;

/** Internal function utilities. */
public class Functions {
  public static <A, B, C> BiFunction<B, A, C> flip(BiFunction<A, B, C> f) {
    return (b, a) -> f.apply(a, b);
  }

  public static <A, B, C> Function<A, Function<B, C>> curry(BiFunction<A, B, C> f) {
    return a -> b -> f.apply(a, b);
  }
}
