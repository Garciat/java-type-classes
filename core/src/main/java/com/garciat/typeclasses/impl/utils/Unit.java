package com.garciat.typeclasses.impl.utils;

public record Unit() {
  public static Unit unit() {
    return new Unit();
  }
}
