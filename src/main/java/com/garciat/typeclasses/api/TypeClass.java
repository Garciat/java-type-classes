package com.garciat.typeclasses.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks an interface as a type class.
 *
 * <p>Type classes are interfaces that define a set of operations that can be implemented for
 * various types. The type class system uses compile-time and runtime reflection to automatically
 * resolve instances.
 *
 * <p><b>PUBLIC API</b>: This is part of the library's public API. Users define and implement type
 * classes using this annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface TypeClass {
  /** Marks a method as a witness (instance) of a type class. */
  @Retention(RetentionPolicy.RUNTIME)
  @interface Witness {
    /**
     * Specifies the overlap behavior for this witness.
     *
     * @return the overlap behavior
     */
    Overlap overlap() default Overlap.NONE;

    /** Defines how instances can overlap with other instances. */
    enum Overlap {
      /** No overlap allowed (default). */
      NONE,
      /** This instance can overlap and take precedence over others. */
      OVERLAPPING,
      /** This instance can be overlapped by others. */
      OVERLAPPABLE
    }
  }
}
