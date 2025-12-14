package com.garciat.typeclasses.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.garciat.typeclasses.api.Ty;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ParsedTypeTest {
  @Test
  void parseClass() {
    ParsedType result = ParsedType.parse(String.class);
    assertEquals(new ParsedType.Const(String.class), result);
  }

  @Test
  void parsePrimitiveType() {
    ParsedType result = ParsedType.parse(int.class);
    assertEquals(new ParsedType.Primitive(int.class), result);
  }

  @Test
  void parseArrayType() {
    ParsedType result = ParsedType.parse(int[].class);
    assertEquals(new ParsedType.ArrayOf(new ParsedType.Primitive(int.class)), result);
  }

  @Test
  void parseObjectArrayType() {
    ParsedType result = ParsedType.parse(String[].class);
    assertEquals(new ParsedType.ArrayOf(new ParsedType.Const(String.class)), result);
  }

  @Test
  void parseParameterizedType() throws Exception {
    Type listType = new Ty<List<String>>() {}.type();
    ParsedType result = ParsedType.parse(listType);

    ParsedType expected =
        new ParsedType.App(new ParsedType.Const(List.class), new ParsedType.Const(String.class));
    assertEquals(expected, result);
  }

  @Test
  void parseMultipleTypeParameters() throws Exception {
    Type mapType = new Ty<Map<String, Integer>>() {}.type();
    ParsedType result = ParsedType.parse(mapType);

    ParsedType expected =
        new ParsedType.App(
            new ParsedType.App(new ParsedType.Const(Map.class), new ParsedType.Const(String.class)),
            new ParsedType.Const(Integer.class));
    assertEquals(expected, result);
  }

  @Test
  void parseNestedParameterizedType() throws Exception {
    Type nestedType = new Ty<List<Optional<String>>>() {}.type();
    ParsedType result = ParsedType.parse(nestedType);

    ParsedType expected =
        new ParsedType.App(
            new ParsedType.Const(List.class),
            new ParsedType.App(
                new ParsedType.Const(Optional.class), new ParsedType.Const(String.class)));
    assertEquals(expected, result);
  }

  @Test
  void parseAll() throws Exception {
    Type[] types = {String.class, Integer.class, int.class};
    List<ParsedType> result = ParsedType.parseAll(types);

    List<ParsedType> expected =
        List.of(
            new ParsedType.Const(String.class),
            new ParsedType.Const(Integer.class),
            new ParsedType.Primitive(int.class));
    assertEquals(expected, result);
  }
}
