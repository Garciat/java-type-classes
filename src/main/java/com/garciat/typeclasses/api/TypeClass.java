package com.garciat.typeclasses.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface TypeClass {
  @Retention(RetentionPolicy.RUNTIME)
  @interface Witness {
    Overlap overlap() default Overlap.NONE;

    enum Overlap {
      NONE,
      OVERLAPPING,
      OVERLAPPABLE
    }
  }
}
