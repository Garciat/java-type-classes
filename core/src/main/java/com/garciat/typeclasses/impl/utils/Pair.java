package com.garciat.typeclasses.impl.utils;

import java.util.function.Function;

public record Pair<A, B>(A fst, B snd) {
  public <X> Pair<X, B> mapFst(Function<A, X> f) {
    return Pair.of(f.apply(fst), snd);
  }

  public <Y> Pair<A, Y> mapSnd(Function<B, Y> f) {
    return Pair.of(fst, f.apply(snd));
  }

  public static <A, B> Pair<A, B> of(A first, B second) {
    return new Pair<>(first, second);
  }
}
