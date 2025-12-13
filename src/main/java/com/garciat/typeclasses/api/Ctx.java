package com.garciat.typeclasses.api;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Context token for capturing type class instances at runtime.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * Show<String> showString = ...;
 * Ctx<Show<String>> ctx = new Ctx<>(showString) {};
 * }</pre>
 *
 * <p><b>PUBLIC API</b>: Used for passing explicit type class instances to witness resolution.
 *
 * @param <T> the type class instance type
 */
public abstract class Ctx<T> {
  private final T instance;

  /**
   * Constructs a context with the given instance.
   *
   * @param instance the type class instance
   */
  public Ctx(T instance) {
    this.instance = instance;
  }

  /**
   * Returns the instance.
   *
   * @return the type class instance
   */
  public T instance() {
    return instance;
  }

  /**
   * Returns the captured type.
   *
   * @return the Type object representing T
   */
  public Type type() {
    return Objects.requireNonNull(
        ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
  }
}
