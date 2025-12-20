package com.garciat.typeclasses.types;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@FunctionalInterface
public interface Gen<A> {
  A generate(long seed, int size);

  default <B> Gen<B> map(Function<A, B> f) {
    return (seed, size) -> f.apply(generate(seed, size));
  }

  // TODO: This is a naive implementation; in a real implementation, the seed
  // management would be
  // more sophisticated.
  default <B> Gen<B> flatMap(Function<A, Gen<B>> f) {
    return (seed, size) -> f.apply(generate(seed, size)).generate(seed + 1, size);
  }

  default Gen<A> variant(int n) {
    return (seed, size) -> generate(seed + n, size);
  }

  default Gen<List<A>> listOf() {
    return sized(size -> chooseInt(0, size).flatMap(this::vectorOf));
  }

  default Gen<List<A>> vectorOf(int length) {
    return (seed, size) -> {
      List<A> result = new ArrayList<>();
      for (int i = 0; i < length; i++) {
        result.add(generate(seed + i, size));
      }
      return result;
    };
  }

  static Gen<Integer> chooseInt(int low, int high) {
    return (seed, size) -> new java.util.Random(seed).nextInt(low, high);
  }

  static <A> Gen<A> sized(Function<Integer, Gen<A>> gen) {
    return (seed, size) -> gen.apply(size).generate(seed, size);
  }
}
