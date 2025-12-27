package com.garciat.typeclasses.testclasses;

import com.garciat.typeclasses.api.TypeClass;

@TypeClass
public interface TestTrivial<A> {
  static <A> TestTrivial<A> trivial() {
    return new TestTrivial<>() {};
  }
}
