package com.garciat.typeclasses.api;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

public abstract class Ctx<T> {
  private final T instance;

  public Ctx(T instance) {
    this.instance = instance;
  }

  public T instance() {
    return instance;
  }

  public Type type() {
    return Objects.requireNonNull(
        ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
  }
}
