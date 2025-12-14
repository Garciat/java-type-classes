package com.garciat.typeclasses.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.impl.ParsedType.App;
import com.garciat.typeclasses.impl.ParsedType.ArrayOf;
import com.garciat.typeclasses.impl.ParsedType.Const;
import com.garciat.typeclasses.impl.ParsedType.Primitive;
import com.garciat.typeclasses.impl.ParsedType.Var;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ParsedTypeTest {
  @Test
  void parseClass() {
    assertThat(ParsedType.parse(String.class)).isEqualTo(new Const(String.class));
  }

  @Test
  void parsePrimitiveType() {
    assertThat(ParsedType.parse(int.class)).isEqualTo(new Primitive(int.class));
  }

  @Test
  void parseArrayType() {
    assertThat(ParsedType.parse(int[].class)).isEqualTo(new ArrayOf(new Primitive(int.class)));
  }

  @Test
  void parseObjectArrayType() {
    assertThat(ParsedType.parse(String[].class)).isEqualTo(new ArrayOf(new Const(String.class)));
  }

  @Test
  void parseParameterizedType() throws Exception {
    Type listType = new Ty<List<String>>() {}.type();

    assertThat(ParsedType.parse(listType))
        .isEqualTo(new App(new Const(List.class), new Const(String.class)));
  }

  @Test
  void parseMultipleTypeParameters() throws Exception {
    Type mapType = new Ty<Map<String, Integer>>() {}.type();

    assertThat(ParsedType.parse(mapType))
        .isEqualTo(
            new App(
                new App(new Const(Map.class), new Const(String.class)), new Const(Integer.class)));
  }

  @Test
  void parseNestedParameterizedType() throws Exception {
    Type nestedType = new Ty<List<Optional<String>>>() {}.type();

    assertThat(ParsedType.parse(nestedType))
        .isEqualTo(
            new App(
                new Const(List.class),
                new App(new Const(Optional.class), new Const(String.class))));
  }

  @Test
  void parseAll() throws Exception {
    Type[] types = {String.class, Integer.class, int.class};

    assertThat(ParsedType.parseAll(types))
        .isEqualTo(
            List.of(new Const(String.class), new Const(Integer.class), new Primitive(int.class)));
  }

  @Test
  void parseTypeVariable() throws Exception {
    class TestClass<T> {}
    TypeVariable<?> tv = TestClass.class.getTypeParameters()[0];

    assertThat(ParsedType.parse(tv)).isEqualTo(new Var(tv));
  }

  @Test
  void parseTypeVariableInParameterizedType() throws Exception {
    class TestClass<T> {
      List<T> field;
    }
    Type fieldType = TestClass.class.getDeclaredField("field").getGenericType();
    TypeVariable<?> tv = TestClass.class.getTypeParameters()[0];

    assertThat(ParsedType.parse(fieldType)).isEqualTo(new App(new Const(List.class), new Var(tv)));
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
