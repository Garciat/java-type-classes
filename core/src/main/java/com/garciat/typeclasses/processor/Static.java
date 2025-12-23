package com.garciat.typeclasses.processor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeVariable;

public final class Static {
  private Static() {}

  public record Method(ExecutableElement java) {}

  public record Var(TypeVariable java) {
    @Override
    public String toString() {
      return java.toString();
    }
  }

  public record Const(TypeElement java) {
    @Override
    public String toString() {
      return java.getSimpleName().toString();
    }
  }

  public record Prim(PrimitiveType java) {
    @Override
    public String toString() {
      return java.toString();
    }
  }
}
