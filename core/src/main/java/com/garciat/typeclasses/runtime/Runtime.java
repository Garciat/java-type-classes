package com.garciat.typeclasses.runtime;

public final class Runtime {
  private Runtime() {}

  public record Method(java.lang.reflect.Method java) {
    @Override
    public String toString() {
      return java.getDeclaringClass().getSimpleName() + "." + java.getName();
    }
  }

  public record Var(java.lang.reflect.TypeVariable<?> java) {
    @Override
    public String toString() {
      return java.getName();
    }
  }

  public record Const(java.lang.Class<?> java) {
    @Override
    public String toString() {
      return java.getSimpleName();
    }
  }

  public record Prim(java.lang.Class<?> java) {
    @Override
    public String toString() {
      return java.getSimpleName();
    }
  }
}
