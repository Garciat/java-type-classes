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

    assertEquals(0, result.paramTypes().size());
    assertEquals(new ParsedType.Const(String.class), result.returnType());
    assertEquals(method, result.java());
  }

  @Test
  void parseMethodWithParameters() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("withParams", Integer.class, String.class);
    FuncType result = FuncType.parse(method);

    assertEquals(2, result.paramTypes().size());
    assertEquals(new ParsedType.Const(Integer.class), result.paramTypes().get(0));
    assertEquals(new ParsedType.Const(String.class), result.paramTypes().get(1));
    assertEquals(new ParsedType.Const(Boolean.class), result.returnType());
  }

  @Test
  void parseMethodWithGenericReturn() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("genericReturn");
    FuncType result = FuncType.parse(method);

    assertEquals(0, result.paramTypes().size());
    assertEquals(
        new ParsedType.App(new ParsedType.Const(List.class), new ParsedType.Const(String.class)),
        result.returnType());
  }

  @Test
  void parseMethodWithGenericParams() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("genericParams", List.class);
    FuncType result = FuncType.parse(method);

    assertEquals(1, result.paramTypes().size());
    assertEquals(
        new ParsedType.App(new ParsedType.Const(List.class), new ParsedType.Const(Integer.class)),
        result.paramTypes().get(0));
  }

  @Test
  void parseMethodWithPrimitives() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("withPrimitives", int.class, boolean.class);
    FuncType result = FuncType.parse(method);

    assertEquals(2, result.paramTypes().size());
    assertEquals(new ParsedType.Primitive(int.class), result.paramTypes().get(0));
    assertEquals(new ParsedType.Primitive(boolean.class), result.paramTypes().get(1));
    assertEquals(new ParsedType.Primitive(void.class), result.returnType());
  }

  @Test
  void parseNonStaticMethodThrows() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("nonStatic");
    assertThrows(IllegalArgumentException.class, () -> FuncType.parse(method));
  }

  @Test
  void formatNoParams() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("simple");
    FuncType funcType = FuncType.parse(method);

    String formatted = funcType.format();
    assertTrue(formatted.contains("() -> String"), "Format should show no params and return type");
  }

  @Test
  void formatWithParams() throws Exception {
    Method method = TestMethods.class.getDeclaredMethod("withParams", Integer.class, String.class);
    FuncType funcType = FuncType.parse(method);

    String formatted = funcType.format();
    assertTrue(formatted.contains("Integer, String"), "Format should show param types");
    assertTrue(formatted.contains("Boolean"), "Format should show return type");
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

    public String nonStatic() {
      return "";
    }
  }
}
