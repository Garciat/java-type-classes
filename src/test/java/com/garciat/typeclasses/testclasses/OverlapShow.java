package com.garciat.typeclasses.testclasses;

import com.garciat.typeclasses.api.TypeClass;

@TypeClass
public interface OverlapShow<A> {
  String show(A a);

  @TypeClass.Witness(overlap = TypeClass.Witness.Overlap.OVERLAPPABLE)
  static <A> OverlapShow<A> genericShow() {
    return a -> "Generic: " + a.toString();
  }

  @TypeClass.Witness(overlap = TypeClass.Witness.Overlap.OVERLAPPING)
  static OverlapShow<Integer> integerShow() {
    return i -> "Integer: " + i;
  }

  @TypeClass.Witness(overlap = TypeClass.Witness.Overlap.OVERLAPPING)
  static OverlapShow<String> stringShow() {
    return s -> "String: " + s;
  }
}
