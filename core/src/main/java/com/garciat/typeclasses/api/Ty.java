package com.garciat.typeclasses.api;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public interface Ty<T> {
  default Type type() {
    return requireNonNull(
        ((ParameterizedType) getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0]);
  }
}
