package com.garciat.typeclasses.impl.utils;

import java.util.List;

public sealed interface ZeroOneMore<A> {
  record Zero<A>() implements ZeroOneMore<A> {}

  record One<A>(A value) implements ZeroOneMore<A> {}

  record More<A>(List<A> values) implements ZeroOneMore<A> {}

  static <A> ZeroOneMore<A> of(List<A> list) {
    return switch (list.size()) {
      case 0 -> new Zero<>();
      case 1 -> new One<>(list.getFirst());
      default -> new More<>(list);
    };
  }
}
