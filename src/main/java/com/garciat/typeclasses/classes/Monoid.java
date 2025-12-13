package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;

@TypeClass
public interface Monoid<A> {
  A combine(A a1, A a2);

  A identity();

  static <A> A combineAll(Monoid<A> monoid, List<A> elements) {
    A result = monoid.identity();
    for (A element : elements) {
      result = monoid.combine(result, element);
    }
    return result;
  }

  static <A> Monoid<A> of(Supplier<A> identity, BinaryOperator<A> combine) {
    return new Monoid<>() {
      @Override
      public A combine(A a1, A a2) {
        return combine.apply(a1, a2);
      }

      @Override
      public A identity() {
        return identity.get();
      }
    };
  }

  @TypeClass.Witness
  static Monoid<String> stringMonoid() {
    return new Monoid<>() {
      @Override
      public String combine(String s1, String s2) {
        return s1 + s2;
      }

      @Override
      public String identity() {
        return "";
      }
    };
  }
}
