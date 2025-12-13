package com.garciat.typeclasses.types;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.classes.Monoid;
import com.garciat.typeclasses.classes.Num;

public record Sum<A>(A value) {
  @TypeClass.Witness
  public static <A> Monoid<Sum<A>> monoid(Num<A> num) {
    return new Monoid<>() {
      @Override
      public Sum<A> combine(Sum<A> s1, Sum<A> s2) {
        return new Sum<>(num.add(s1.value(), s2.value()));
      }

      @Override
      public Sum<A> identity() {
        return new Sum<>(num.zero());
      }
    };
  }
}
