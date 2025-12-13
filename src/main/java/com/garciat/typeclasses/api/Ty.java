package com.garciat.typeclasses.api;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

public interface Ty<T> {
  default Type type() {
    return Objects.requireNonNull(
        ((ParameterizedType) getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0]);
  }
}
