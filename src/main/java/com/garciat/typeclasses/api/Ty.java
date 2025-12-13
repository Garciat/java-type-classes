package com.garciat.typeclasses.api;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Type token for capturing type information at runtime.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * Show<String> showString = TypeClasses.witness(new Ty<Show<String>>() {});
 * }</pre>
 *
 * <p><b>PUBLIC API</b>: This is the main interface users interact with to summon type class
 * instances.
 *
 * @param <T> the type being captured
 */
public interface Ty<T> {
  /**
   * Returns the captured type.
   *
   * @return the Type object representing T
   */
  default Type type() {
    return Objects.requireNonNull(
        ((ParameterizedType) getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0]);
  }
}
