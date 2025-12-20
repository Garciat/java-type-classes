package com.garciat.typeclasses.utils;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Lists {
  private Lists() {}

  public static <A, B> List<B> map(List<A> list, Function<? super A, ? extends B> f) {
    return list.stream().map(f).collect(Collectors.toList());
  }

  @SafeVarargs
  public static <A> List<A> concat(List<A>... lists) {
    return Arrays.stream(lists).flatMap(List::stream).toList();
  }
}
