package com.garciat.typeclasses.impl;

import static org.assertj.core.api.Assertions.*;

import com.garciat.typeclasses.impl.ParsedType.App;
import com.garciat.typeclasses.impl.ParsedType.ArrayOf;
import com.garciat.typeclasses.impl.ParsedType.Const;
import com.garciat.typeclasses.impl.ParsedType.Primitive;
import com.garciat.typeclasses.impl.ParsedType.Var;
import com.garciat.typeclasses.types.Maybe;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class UnificationTest {
  @Test
  void unifyEqualConsts() {
    ParsedType t1 = new Const(String.class);
    ParsedType t2 = new Const(String.class);

    Maybe<Map<Var, ParsedType>> result = Unification.unify(t1, t2);

    assertThat(result).isEqualTo(Maybe.just(Map.of()));
  }

  @Test
  void unifyDifferentConsts() {
    ParsedType t1 = new Const(String.class);
    ParsedType t2 = new Const(Integer.class);

    Maybe<Map<Var, ParsedType>> result = Unification.unify(t1, t2);

    assertThat(result).isEqualTo(Maybe.nothing());
  }

  @Test
  void unifyVarWithConst() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType t1 = new Var(tv);
    ParsedType t2 = new Const(String.class);

    Maybe<Map<Var, ParsedType>> result = Unification.unify(t1, t2);

    assertThat(result).isEqualTo(Maybe.just(Map.of(t1, t2)));
  }

  @Test
  void unifyVarWithPrimitiveFails() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType t1 = new Var(tv);
    ParsedType t2 = new Primitive(int.class);

    Maybe<Map<Var, ParsedType>> result = Unification.unify(t1, t2);

    assertThat(result).isEqualTo(Maybe.nothing());
  }

  @Test
  void unifyApps() {
    ParsedType list1 = new App(new Const(List.class), new Const(String.class));
    ParsedType list2 = new App(new Const(List.class), new Const(String.class));

    Maybe<Map<Var, ParsedType>> result = Unification.unify(list1, list2);

    assertThat(result).isEqualTo(Maybe.just(Map.of()));
  }

  @Test
  void unifyAppsDifferentArgs() {
    ParsedType list1 = new App(new Const(List.class), new Const(String.class));
    ParsedType list2 = new App(new Const(List.class), new Const(Integer.class));

    Maybe<Map<Var, ParsedType>> result = Unification.unify(list1, list2);

    assertThat(result).isEqualTo(Maybe.nothing());
  }

  @Test
  void unifyAppsWithVar() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    Var var = new Var(tv);
    ParsedType list1 = new App(new Const(List.class), var);
    ParsedType list2 = new App(new Const(List.class), new Const(String.class));

    Maybe<Map<Var, ParsedType>> result = Unification.unify(list1, list2);

    assertThat(result).isEqualTo(Maybe.just(Map.of(var, new Const(String.class))));
  }

  @Test
  void unifyNestedAppsWithVar() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    Var var = new Var(tv);
    ParsedType map1 = new App(new App(new Const(Map.class), var), new Const(Integer.class));
    ParsedType map2 =
        new App(new App(new Const(Map.class), new Const(String.class)), new Const(Integer.class));

    Maybe<Map<Var, ParsedType>> result = Unification.unify(map1, map2);

    assertThat(result).isEqualTo(Maybe.just(Map.of(var, new Const(String.class))));
  }

  @Test
  void unifyArrays() {
    ParsedType arr1 = new ArrayOf(new Const(String.class));
    ParsedType arr2 = new ArrayOf(new Const(String.class));

    Maybe<Map<Var, ParsedType>> result = Unification.unify(arr1, arr2);

    assertThat(result).isEqualTo(Maybe.just(Map.of()));
  }

  @Test
  void unifyPrimitives() {
    ParsedType p1 = new Primitive(int.class);
    ParsedType p2 = new Primitive(int.class);

    Maybe<Map<Var, ParsedType>> result = Unification.unify(p1, p2);

    assertThat(result).isEqualTo(Maybe.just(Map.of()));
  }

  @Test
  void unifyDifferentPrimitives() {
    ParsedType p1 = new Primitive(int.class);
    ParsedType p2 = new Primitive(boolean.class);

    Maybe<Map<Var, ParsedType>> result = Unification.unify(p1, p2);

    assertThat(result).isEqualTo(Maybe.nothing());
  }

  @Test
  void substituteVar() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    Var var = new Var(tv);
    ParsedType replacement = new Const(String.class);

    ParsedType result = Unification.substitute(Map.of(var, replacement), var);

    assertThat(result).isEqualTo(replacement);
  }

  @Test
  void substituteConst() {
    ParsedType type = new Const(String.class);

    ParsedType result = Unification.substitute(Map.of(), type);

    assertThat(result).isEqualTo(type);
  }

  @Test
  void substituteApp() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    Var var = new Var(tv);
    ParsedType replacement = new Const(String.class);

    ParsedType result =
        Unification.substitute(Map.of(var, replacement), new App(new Const(List.class), var));

    assertThat(result).isEqualTo(new App(new Const(List.class), replacement));
  }

  @Test
  void substituteArrayOf() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    Var var = new Var(tv);
    ParsedType replacement = new Const(String.class);

    ParsedType result = Unification.substitute(Map.of(var, replacement), new ArrayOf(var));

    assertThat(result).isEqualTo(new ArrayOf(replacement));
  }

  @Test
  void substituteAll() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    Var var = new Var(tv);
    ParsedType replacement = new Const(String.class);

    List<ParsedType> result =
        Unification.substituteAll(
            Map.of(var, replacement),
            List.of(var, new Const(Integer.class), new App(new Const(List.class), var)));

    assertThat(result)
        .isEqualTo(
            List.of(
                replacement,
                new Const(Integer.class),
                new App(new Const(List.class), replacement)));
  }

  // Helper to get a type variable for testing
  private TypeVariable<?> getTypeVariable() throws Exception {
    class TestClass<T> {}
    return TestClass.class.getTypeParameters()[0];
  }
}
