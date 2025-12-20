package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;

@TypeClass
public sealed interface TyEq<A, B> {
  A castR(B b);

  B castL(A a);

  static <T> TyEq<T, T> refl() {
    return new Refl<>();
  }

  record Refl<T>() implements TyEq<T, T> {

    @Override
    public T castR(T t) {
      return t;
    }

    @Override
    public T castL(T t) {
      return t;
    }
  }

  @TypeClass.Witness
  static <T> TyEq<T, T> tyEqRefl() {
    return refl();
  }
}
