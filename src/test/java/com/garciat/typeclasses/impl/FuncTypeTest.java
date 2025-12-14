package com.garciat.typeclasses.impl;

import static org.assertj.core.api.Assertions.*;

import com.garciat.typeclasses.impl.ParsedType.App;
import com.garciat.typeclasses.impl.ParsedType.Const;
import com.garciat.typeclasses.impl.ParsedType.Primitive;
import com.garciat.typeclasses.impl.ParsedType.Var;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FuncTypeTest {
  @Test
  void parseSimpleStaticMethod() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("simple");

    assertThat(FuncType.parse(method))
        .isEqualTo(new FuncType(method, List.of(), new Const(String.class)));
  }

  @Test
  void parseMethodWithParameters() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("withParams", Integer.class, String.class);

    assertThat(FuncType.parse(method))
        .isEqualTo(
            new FuncType(
                method,
                List.of(new Const(Integer.class), new Const(String.class)),
                new Const(Boolean.class)));
  }

  @Test
  void parseMethodWithGenericReturn() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("genericReturn");

    assertThat(FuncType.parse(method))
        .isEqualTo(
            new FuncType(
                method, List.of(), new App(new Const(List.class), new Const(String.class))));
  }

  @Test
  void parseMethodWithGenericParams() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("genericParams", List.class);

    assertThat(FuncType.parse(method))
        .isEqualTo(
            new FuncType(
                method,
                List.of(new App(new Const(List.class), new Const(Integer.class))),
                new Primitive(void.class)));
  }

  @Test
  void parseMethodWithPrimitives() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("withPrimitives", int.class, boolean.class);

    assertThat(FuncType.parse(method))
        .isEqualTo(
            new FuncType(
                method,
                List.of(new Primitive(int.class), new Primitive(boolean.class)),
                new Primitive(void.class)));
  }

  @Test
  void parseNonStaticMethodThrows() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("nonStatic");
    assertThatThrownBy(() -> FuncType.parse(method)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseGenericMethodWithTypeParameters() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("genericMethod", Object.class);

    // The method has a type parameter T, so we expect a Var in the return type
    assertThat(FuncType.parse(method))
        .isEqualTo(
            new FuncType(
                method,
                List.of(new Var(method.getTypeParameters()[0])),
                new Var(method.getTypeParameters()[0])));
  }

  // Test helper class with various method signatures
  private static class TestMethods {
    public static String simple() {
      return "";
    }

    public static Boolean withParams(Integer i, String s) {
      return true;
    }

    public static List<String> genericReturn() {
      return List.of();
    }

    public static void genericParams(List<Integer> list) {}

    public static void withPrimitives(int i, boolean b) {}

    public static <T> T genericMethod(T value) {
      return value;
    }

    public String nonStatic() {
      return "";
    }
  }
}
