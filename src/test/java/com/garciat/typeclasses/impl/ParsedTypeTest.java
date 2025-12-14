package com.garciat.typeclasses.impl;

import static org.assertj.core.api.Assertions.*;

import com.garciat.typeclasses.api.Ty;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ParsedTypeTest {
  @Test
  void parseClass() {
    ParsedType result = ParsedType.parse(String.class);
    assertThat(result).isEqualTo(new ParsedType.Const(String.class));
  }

  @Test
  void parsePrimitiveType() {
    ParsedType result = ParsedType.parse(int.class);
    assertThat(result).isEqualTo(new ParsedType.Primitive(int.class));
  }

  @Test
  void parseArrayType() {
    ParsedType result = ParsedType.parse(int[].class);
    assertThat(result).isEqualTo(new ParsedType.ArrayOf(new ParsedType.Primitive(int.class)));
  }

  @Test
  void parseObjectArrayType() {
    ParsedType result = ParsedType.parse(String[].class);
    assertThat(result).isEqualTo(new ParsedType.ArrayOf(new ParsedType.Const(String.class)));
  }

  @Test
  void parseParameterizedType() throws Exception {
    Type listType = new Ty<List<String>>() {}.type();
    ParsedType result = ParsedType.parse(listType);

    ParsedType expected =
        new ParsedType.App(new ParsedType.Const(List.class), new ParsedType.Const(String.class));
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void parseMultipleTypeParameters() throws Exception {
    Type mapType = new Ty<Map<String, Integer>>() {}.type();
    ParsedType result = ParsedType.parse(mapType);

    ParsedType expected =
        new ParsedType.App(
            new ParsedType.App(new ParsedType.Const(Map.class), new ParsedType.Const(String.class)),
            new ParsedType.Const(Integer.class));
    assertThat(result).isEqualTo(expected);
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
    assertThat(result).isEqualTo(expected);
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
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void parseTypeVariable() throws Exception {
    class TestClass<T> {}
    TypeVariable<?> tv = TestClass.class.getTypeParameters()[0];

    ParsedType result = ParsedType.parse(tv);

    assertThat(result).isEqualTo(new ParsedType.Var(tv));
  }

  @Test
  void parseTypeVariableInParameterizedType() throws Exception {
    class TestClass<T> {
      List<T> field;
    }
    Type fieldType = TestClass.class.getDeclaredField("field").getGenericType();
    TypeVariable<?> tv = TestClass.class.getTypeParameters()[0];

    ParsedType result = ParsedType.parse(fieldType);

    ParsedType expected =
        new ParsedType.App(new ParsedType.Const(List.class), new ParsedType.Var(tv));
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void parseWildcardTypeThrows() throws Exception {
    class TestClass {
      List<?> field;
    }
    Type fieldType = TestClass.class.getDeclaredField("field").getGenericType();

    assertThatThrownBy(() -> ParsedType.parse(fieldType))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wildcard");
  }
}
