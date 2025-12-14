package com.garciat.typeclasses.testclasses;

import com.garciat.typeclasses.api.TypeClass;

@TypeClass
public interface AmbiguousShow<A> {
  String show(A a);

  // Two witnesses without overlap markers - should cause ambiguity
  @TypeClass.Witness
  static <A> AmbiguousShow<A> witness1() {
    return a -> "witness1";
  }

  @TypeClass.Witness
  static <A> AmbiguousShow<A> witness2() {
    return a -> "witness2";
  }
}
