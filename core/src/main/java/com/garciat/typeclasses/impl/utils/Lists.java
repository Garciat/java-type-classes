package com.garciat.typeclasses.impl.utils;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Lists {
  private Lists() {}

  public static <A, B> List<B> map(A[] list, Function<? super A, ? extends B> f) {
    return map(Arrays.asList(list), f);
  }

  public static <A, B> List<B> map(List<A> list, Function<? super A, ? extends B> f) {
    return list.stream().map(f).collect(Collectors.toList());
  }

  public static <A, B, C> List<C> zip(
      List<A> list1, List<B> list2, BiFunction<? super A, ? super B, ? extends C> f) {
    if (list1.size() != list2.size()) {
      throw new IllegalArgumentException("Lists must have the same size to be zipped.");
    }
    int size = list1.size();
    return IntStream.range(0, size)
        .mapToObj(i -> f.apply(list1.get(i), list2.get(i)))
        .collect(Collectors.toList());
  }

  @SafeVarargs
  public static <A> List<A> concat(List<A>... lists) {
    return Arrays.stream(lists).flatMap(List::stream).toList();
  }
}
