package com.garciat.typeclasses.types;

public record Unit() {
  public static Unit unit() {
    return new Unit();
  }
}
