package com.garciat.typeclasses.impl.utils;

import java.util.function.Function;
import java.util.stream.Stream;

public final class Streams {
  private Streams() {}

  public static <T extends U, U> Function<U, Stream<T>> isInstanceOf(Class<T> cls) {
    return u -> cls.isInstance(u) ? Stream.of(cls.cast(u)) : Stream.empty();
  }
}
