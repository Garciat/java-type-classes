package com.garciat.typeclasses.impl.utils;

import java.util.HashMap;
import java.util.Map;

public final class Maps {
  private Maps() {}

  public static <K, V> Map<K, V> merge(Map<K, V> m1, Map<K, V> m2) {
    Map<K, V> result = new HashMap<>(m1);
    for (Map.Entry<K, V> entry : m2.entrySet()) {
      V existing = result.put(entry.getKey(), entry.getValue());
      if (existing != null && !existing.equals(entry.getValue())) {
        throw new IllegalArgumentException("Duplicate key: " + entry.getKey());
      }
    }
    return result;
  }
}
