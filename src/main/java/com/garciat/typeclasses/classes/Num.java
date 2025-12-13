package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;

@TypeClass
public interface Num<A> {
  A add(A a1, A a2);

  A mul(A a1, A a2);

  A zero();

  A one();

  @TypeClass.Witness
  static Num<Integer> integerNum() {
    return new Num<>() {
      @Override
      public Integer add(Integer a1, Integer a2) {
        return a1 + a2;
      }

      @Override
      public Integer mul(Integer a1, Integer a2) {
        return a1 * a2;
      }

      @Override
      public Integer zero() {
        return 0;
      }

      @Override
      public Integer one() {
        return 1;
      }
    };
  }
}
