package com.garciat.typeclasses.impl;

import static org.assertj.core.api.Assertions.*;

import com.garciat.typeclasses.types.Maybe;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class UnificationTest {
  @Test
  void unifyEqualConsts() {
    ParsedType t1 = new ParsedType.Const(String.class);
    ParsedType t2 = new ParsedType.Const(String.class);

    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(t1, t2);

    assertThat(result).isEqualTo(Maybe.just(Map.of()));
  }

  @Test
  void unifyDifferentConsts() {
    ParsedType t1 = new ParsedType.Const(String.class);
    ParsedType t2 = new ParsedType.Const(Integer.class);

    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(t1, t2);

    assertThat(result).isEqualTo(Maybe.nothing());
  }

  @Test
  void unifyVarWithConst() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType t1 = new ParsedType.Var(tv);
    ParsedType t2 = new ParsedType.Const(String.class);

    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(t1, t2);

    assertThat(result).isEqualTo(Maybe.just(Map.of(t1, t2)));
  }

  @Test
  void unifyVarWithPrimitiveFails() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType t1 = new ParsedType.Var(tv);
    ParsedType t2 = new ParsedType.Primitive(int.class);

    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(t1, t2);

    assertThat(result).isEqualTo(Maybe.nothing());
  }

  @Test
  void unifyApps() {
    ParsedType list1 =
        new ParsedType.App(new ParsedType.Const(List.class), new ParsedType.Const(String.class));
    ParsedType list2 =
        new ParsedType.App(new ParsedType.Const(List.class), new ParsedType.Const(String.class));

    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(list1, list2);

    assertThat(result).isEqualTo(Maybe.just(Map.of()));
  }

  @Test
  void unifyAppsDifferentArgs() {
    ParsedType list1 =
        new ParsedType.App(new ParsedType.Const(List.class), new ParsedType.Const(String.class));
    ParsedType list2 =
        new ParsedType.App(new ParsedType.Const(List.class), new ParsedType.Const(Integer.class));

    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(list1, list2);

    assertThat(result).isEqualTo(Maybe.nothing());
  }

  @Test
  void unifyAppsWithVar() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType.Var var = new ParsedType.Var(tv);
    ParsedType list1 = new ParsedType.App(new ParsedType.Const(List.class), var);
    ParsedType list2 =
        new ParsedType.App(new ParsedType.Const(List.class), new ParsedType.Const(String.class));

    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(list1, list2);

    assertThat(result).isEqualTo(Maybe.just(Map.of(var, new ParsedType.Const(String.class))));
  }

  @Test
  void unifyNestedAppsWithVar() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType.Var var = new ParsedType.Var(tv);
    ParsedType map1 =
        new ParsedType.App(
            new ParsedType.App(new ParsedType.Const(Map.class), var),
            new ParsedType.Const(Integer.class));
    ParsedType map2 =
        new ParsedType.App(
            new ParsedType.App(new ParsedType.Const(Map.class), new ParsedType.Const(String.class)),
            new ParsedType.Const(Integer.class));

    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(map1, map2);

    assertThat(result).isEqualTo(Maybe.just(Map.of(var, new ParsedType.Const(String.class))));
  }

  @Test
  void unifyArrays() {
    ParsedType arr1 = new ParsedType.ArrayOf(new ParsedType.Const(String.class));
    ParsedType arr2 = new ParsedType.ArrayOf(new ParsedType.Const(String.class));

    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(arr1, arr2);

    assertThat(result).isEqualTo(Maybe.just(Map.of()));
  }

  @Test
  void unifyPrimitives() {
    ParsedType p1 = new ParsedType.Primitive(int.class);
    ParsedType p2 = new ParsedType.Primitive(int.class);

    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(p1, p2);

    assertThat(result).isEqualTo(Maybe.just(Map.of()));
  }

  @Test
  void unifyDifferentPrimitives() {
    ParsedType p1 = new ParsedType.Primitive(int.class);
    ParsedType p2 = new ParsedType.Primitive(boolean.class);

    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(p1, p2);

    assertThat(result).isEqualTo(Maybe.nothing());
  }

  @Test
  void substituteVar() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType.Var var = new ParsedType.Var(tv);
    ParsedType replacement = new ParsedType.Const(String.class);

    ParsedType result = Unification.substitute(Map.of(var, replacement), var);

    assertThat(result).isEqualTo(replacement);
  }

  @Test
  void substituteConst() {
    ParsedType type = new ParsedType.Const(String.class);

    ParsedType result = Unification.substitute(Map.of(), type);

    assertThat(result).isEqualTo(type);
  }

  @Test
  void substituteApp() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType.Var var = new ParsedType.Var(tv);
    ParsedType replacement = new ParsedType.Const(String.class);

    ParsedType result =
        Unification.substitute(
            Map.of(var, replacement), new ParsedType.App(new ParsedType.Const(List.class), var));

    assertThat(result).isEqualTo(new ParsedType.App(new ParsedType.Const(List.class), replacement));
  }

  @Test
  void substituteArrayOf() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType.Var var = new ParsedType.Var(tv);
    ParsedType replacement = new ParsedType.Const(String.class);

    ParsedType result =
        Unification.substitute(Map.of(var, replacement), new ParsedType.ArrayOf(var));

    assertThat(result).isEqualTo(new ParsedType.ArrayOf(replacement));
  }

  @Test
  void substituteAll() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType.Var var = new ParsedType.Var(tv);
    ParsedType replacement = new ParsedType.Const(String.class);

    List<ParsedType> result =
        Unification.substituteAll(
            Map.of(var, replacement),
            List.of(
                var,
                new ParsedType.Const(Integer.class),
                new ParsedType.App(new ParsedType.Const(List.class), var)));

    assertThat(result)
        .isEqualTo(
            List.of(
                replacement,
                new ParsedType.Const(Integer.class),
                new ParsedType.App(new ParsedType.Const(List.class), replacement)));
  }

  // Helper to get a type variable for testing
  private TypeVariable<?> getTypeVariable() throws Exception {
    class TestClass<T> {}
    return TestClass.class.getTypeParameters()[0];
  }
}
