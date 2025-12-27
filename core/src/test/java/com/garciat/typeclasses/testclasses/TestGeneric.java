package com.garciat.typeclasses.testclasses;

import com.garciat.typeclasses.api.Out;
import com.garciat.typeclasses.api.TypeClass;

@TypeClass
public interface TestGeneric<A, @Out Rep> {
  Rep from(A value);

  A to(Rep rep);
}
