package com.garciat.typeclasses.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TypeClass {
  @Target(ElementType.METHOD)
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
