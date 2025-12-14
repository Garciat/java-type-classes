package com.garciat.typeclasses.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.ParameterizedType;
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
    assertEquals(
        new ParsedType.ArrayOf(new ParsedType.Primitive(int.class)),
        result);
  }

  @Test
  void parseObjectArrayType() {
    ParsedType result = ParsedType.parse(String[].class);
    assertEquals(
        new ParsedType.ArrayOf(new ParsedType.Const(String.class)),
        result);
  }

  @Test
  void parseParameterizedType() throws Exception {
    Type listType = new TypeToken<List<String>>() {}.type();
    ParsedType result = ParsedType.parse(listType);
    
    ParsedType expected = new ParsedType.App(
        new ParsedType.Const(List.class),
        new ParsedType.Const(String.class)
    );
    assertEquals(expected, result);
  }

  @Test
  void parseMultipleTypeParameters() throws Exception {
    Type mapType = new TypeToken<Map<String, Integer>>() {}.type();
    ParsedType result = ParsedType.parse(mapType);
    
    ParsedType expected = new ParsedType.App(
        new ParsedType.App(
            new ParsedType.Const(Map.class),
            new ParsedType.Const(String.class)
        ),
        new ParsedType.Const(Integer.class)
    );
    assertEquals(expected, result);
  }

  @Test
  void parseNestedParameterizedType() throws Exception {
    Type nestedType = new TypeToken<List<Optional<String>>>() {}.type();
    ParsedType result = ParsedType.parse(nestedType);
    
    ParsedType expected = new ParsedType.App(
        new ParsedType.Const(List.class),
        new ParsedType.App(
            new ParsedType.Const(Optional.class),
            new ParsedType.Const(String.class)
        )
    );
    assertEquals(expected, result);
  }

  @Test
  void formatConst() {
    ParsedType type = new ParsedType.Const(String.class);
    assertEquals("String", type.format());
  }

  @Test
  void formatApp() {
    ParsedType type = new ParsedType.App(
        new ParsedType.Const(List.class),
        new ParsedType.Const(String.class)
    );
    assertEquals("List[E](String)", type.format());
  }

  @Test
  void formatArrayOf() {
    ParsedType type = new ParsedType.ArrayOf(new ParsedType.Primitive(int.class));
    assertEquals("int[]", type.format());
  }

  @Test
  void formatPrimitive() {
    ParsedType type = new ParsedType.Primitive(int.class);
    assertEquals("int", type.format());
  }

  @Test
  void parseAll() throws Exception {
    Type[] types = {String.class, Integer.class, int.class};
    List<ParsedType> result = ParsedType.parseAll(types);
    
    List<ParsedType> expected = List.of(
        new ParsedType.Const(String.class),
        new ParsedType.Const(Integer.class),
        new ParsedType.Primitive(int.class)
    );
    assertEquals(expected, result);
  }

  // Helper class to capture generic type information
  private abstract static class TypeToken<T> {
    Type type() {
      Type superclass = getClass().getGenericSuperclass();
      if (superclass instanceof ParameterizedType pt) {
        return pt.getActualTypeArguments()[0];
      }
      throw new IllegalStateException("TypeToken requires type parameter");
    }
  }
}
