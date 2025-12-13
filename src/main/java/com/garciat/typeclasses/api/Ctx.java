package com.garciat.typeclasses.api;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class Ctx<T> {
  private final T instance;

  public Ctx(T instance) {
    this.instance = instance;
  }

  public T instance() {
    return instance;
  }

  public Type type() {
    return requireNonNull(
        ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
  }
}
