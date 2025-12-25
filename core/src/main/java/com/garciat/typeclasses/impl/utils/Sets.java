package com.garciat.typeclasses.impl.utils;

import java.util.Set;

public final class Sets {
  private Sets() {}

  public static <T> Set<T> difference(Set<T> a, Set<T> b) {
    Set<T> result = new java.util.HashSet<>(a);
    result.removeAll(b);
    return result;
  }
}
