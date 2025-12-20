package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import java.util.Optional;

@TypeClass
public interface Ord<A> extends Eq<A> {
  Ordering compare(A a1, A a2);

  @Override
  default boolean eq(A a1, A a2) {
    return compare(a1, a2) == Ordering.EQ;
  }

  static <A> Ordering compare(Ord<A> ordA, A a1, A a2) {
    return ordA.compare(a1, a2);
  }

  static <A> boolean lt(Ord<A> ordA, A a1, A a2) {
    return ordA.compare(a1, a2) == Ordering.LT;
  }

  @TypeClass.Witness
  static Ord<Integer> integerOrd() {
    return (a1, a2) -> a1 < a2 ? Ordering.LT : a1 > a2 ? Ordering.GT : Ordering.EQ;
  }

  @TypeClass.Witness
  static <A> Ord<Optional<A>> optionalOrd(Ord<A> ordA) {
    return (optA1, optA2) -> {
      if (optA1.isPresent() && optA2.isPresent()) {
        return ordA.compare(optA1.get(), optA2.get());
      } else if (optA1.isEmpty() && optA2.isEmpty()) {
        return Ordering.EQ;
      } else if (optA1.isEmpty()) {
        return Ordering.LT;
      } else {
        return Ordering.GT;
      }
    };
  }
}
