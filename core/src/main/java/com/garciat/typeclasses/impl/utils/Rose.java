package com.garciat.typeclasses.impl.utils;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public sealed interface Rose<A> {
  record Node<A>(A value, List<Rose<A>> children) implements Rose<A> {}

  record Delayed<A>(Supplier<Rose<A>> supplier) implements Rose<A> {}

  default <R> R fold(BiFunction<A, List<R>, R> f) {
    return switch (this) {
      case Node<A> node -> f.apply(node.value(), Lists.map(node.children(), c -> c.fold(f)));
      case Delayed<A> delayed -> delayed.supplier().get().fold(f);
    };
  }

  static <A> Rose<A> of(A value, List<Rose<A>> children) {
    return new Node<>(value, children);
  }

  static <A> Rose<A> delay(Supplier<Rose<A>> supplier) {
    return new Delayed<>(supplier);
  }
}
