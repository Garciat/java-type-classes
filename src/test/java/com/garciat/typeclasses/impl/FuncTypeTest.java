package com.garciat.typeclasses.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FuncTypeTest {
  @Test
  void parseSimpleStaticMethod() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("simple");
    FuncType result = FuncType.parse(method);

    FuncType expected = new FuncType(method, List.of(), new ParsedType.Const(String.class));
    assertEquals(expected, result);
  }

  @Test
  void parseMethodWithParameters() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("withParams", Integer.class, String.class);
    FuncType result = FuncType.parse(method);

    FuncType expected =
        new FuncType(
            method,
            List.of(new ParsedType.Const(Integer.class), new ParsedType.Const(String.class)),
            new ParsedType.Const(Boolean.class));
    assertEquals(expected, result);
  }

  @Test
  void parseMethodWithGenericReturn() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("genericReturn");
    FuncType result = FuncType.parse(method);

    FuncType expected =
        new FuncType(
            method,
            List.of(),
            new ParsedType.App(
                new ParsedType.Const(List.class), new ParsedType.Const(String.class)));
    assertEquals(expected, result);
  }

  @Test
  void parseMethodWithGenericParams() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("genericParams", List.class);
    FuncType result = FuncType.parse(method);

    FuncType expected =
        new FuncType(
            method,
            List.of(
                new ParsedType.App(
                    new ParsedType.Const(List.class), new ParsedType.Const(Integer.class))),
            new ParsedType.Primitive(void.class));
    assertEquals(expected, result);
  }

  @Test
  void parseMethodWithPrimitives() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("withPrimitives", int.class, boolean.class);
    FuncType result = FuncType.parse(method);

    FuncType expected =
        new FuncType(
            method,
            List.of(new ParsedType.Primitive(int.class), new ParsedType.Primitive(boolean.class)),
            new ParsedType.Primitive(void.class));
    assertEquals(expected, result);
  }

  @Test
  void parseNonStaticMethodThrows() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("nonStatic");
    assertThrows(IllegalArgumentException.class, () -> FuncType.parse(method));
  }

  @Test
  void parseGenericMethodWithTypeParameters() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("genericMethod", Object.class);
    FuncType result = FuncType.parse(method);

    // The method has a type parameter T, so we expect a Var in the return type
    FuncType expected =
        new FuncType(
            method,
            List.of(new ParsedType.Var(method.getTypeParameters()[0])),
            new ParsedType.Var(method.getTypeParameters()[0]));
    assertEquals(expected, result);
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
