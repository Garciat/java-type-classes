package com.garciat.typeclasses.impl.utils;

import java.util.List;

public sealed interface Rose<A> {
  record Node<A>(A value, List<Rose<A>> children) implements Rose<A> {}

  static <A> Rose<A> of(A value, List<Rose<A>> children) {
    return new Node<>(value, children);
  }
}
