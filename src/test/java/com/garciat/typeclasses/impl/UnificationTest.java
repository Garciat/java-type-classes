package com.garciat.typeclasses.impl;

import static org.junit.jupiter.api.Assertions.*;

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
    
    assertTrue(result instanceof Maybe.Just);
    assertEquals(Map.of(), ((Maybe.Just<Map<ParsedType.Var, ParsedType>>) result).value());
  }

  @Test
  void unifyDifferentConsts() {
    ParsedType t1 = new ParsedType.Const(String.class);
    ParsedType t2 = new ParsedType.Const(Integer.class);
    
    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(t1, t2);
    
    assertTrue(result instanceof Maybe.Nothing);
  }

  @Test
  void unifyVarWithConst() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType t1 = new ParsedType.Var(tv);
    ParsedType t2 = new ParsedType.Const(String.class);
    
    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(t1, t2);
    
    assertTrue(result instanceof Maybe.Just);
    Map<ParsedType.Var, ParsedType> map = ((Maybe.Just<Map<ParsedType.Var, ParsedType>>) result).value();
    assertEquals(1, map.size());
    assertEquals(t2, map.get(t1));
  }

  @Test
  void unifyVarWithPrimitiveFails() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType t1 = new ParsedType.Var(tv);
    ParsedType t2 = new ParsedType.Primitive(int.class);
    
    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(t1, t2);
    
    assertTrue(result instanceof Maybe.Nothing);
  }

  @Test
  void unifyApps() {
    ParsedType list1 = new ParsedType.App(
        new ParsedType.Const(List.class),
        new ParsedType.Const(String.class)
    );
    ParsedType list2 = new ParsedType.App(
        new ParsedType.Const(List.class),
        new ParsedType.Const(String.class)
    );
    
    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(list1, list2);
    
    assertTrue(result instanceof Maybe.Just);
    assertEquals(Map.of(), ((Maybe.Just<Map<ParsedType.Var, ParsedType>>) result).value());
  }

  @Test
  void unifyAppsDifferentArgs() {
    ParsedType list1 = new ParsedType.App(
        new ParsedType.Const(List.class),
        new ParsedType.Const(String.class)
    );
    ParsedType list2 = new ParsedType.App(
        new ParsedType.Const(List.class),
        new ParsedType.Const(Integer.class)
    );
    
    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(list1, list2);
    
    assertTrue(result instanceof Maybe.Nothing);
  }

  @Test
  void unifyArrays() {
    ParsedType arr1 = new ParsedType.ArrayOf(new ParsedType.Const(String.class));
    ParsedType arr2 = new ParsedType.ArrayOf(new ParsedType.Const(String.class));
    
    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(arr1, arr2);
    
    assertTrue(result instanceof Maybe.Just);
    assertEquals(Map.of(), ((Maybe.Just<Map<ParsedType.Var, ParsedType>>) result).value());
  }

  @Test
  void unifyPrimitives() {
    ParsedType p1 = new ParsedType.Primitive(int.class);
    ParsedType p2 = new ParsedType.Primitive(int.class);
    
    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(p1, p2);
    
    assertTrue(result instanceof Maybe.Just);
    assertEquals(Map.of(), ((Maybe.Just<Map<ParsedType.Var, ParsedType>>) result).value());
  }

  @Test
  void unifyDifferentPrimitives() {
    ParsedType p1 = new ParsedType.Primitive(int.class);
    ParsedType p2 = new ParsedType.Primitive(boolean.class);
    
    Maybe<Map<ParsedType.Var, ParsedType>> result = Unification.unify(p1, p2);
    
    assertTrue(result instanceof Maybe.Nothing);
  }

  @Test
  void substituteVar() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType.Var var = new ParsedType.Var(tv);
    ParsedType replacement = new ParsedType.Const(String.class);
    Map<ParsedType.Var, ParsedType> map = Map.of(var, replacement);
    
    ParsedType result = Unification.substitute(map, var);
    
    assertEquals(replacement, result);
  }

  @Test
  void substituteConst() {
    ParsedType type = new ParsedType.Const(String.class);
    Map<ParsedType.Var, ParsedType> map = Map.of();
    
    ParsedType result = Unification.substitute(map, type);
    
    assertEquals(type, result);
  }

  @Test
  void substituteApp() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType.Var var = new ParsedType.Var(tv);
    ParsedType app = new ParsedType.App(
        new ParsedType.Const(List.class),
        var
    );
    ParsedType replacement = new ParsedType.Const(String.class);
    Map<ParsedType.Var, ParsedType> map = Map.of(var, replacement);
    
    ParsedType result = Unification.substitute(map, app);
    
    ParsedType expected = new ParsedType.App(
        new ParsedType.Const(List.class),
        replacement
    );
    assertEquals(expected, result);
  }

  @Test
  void substituteArrayOf() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType.Var var = new ParsedType.Var(tv);
    ParsedType arrayType = new ParsedType.ArrayOf(var);
    ParsedType replacement = new ParsedType.Const(String.class);
    Map<ParsedType.Var, ParsedType> map = Map.of(var, replacement);
    
    ParsedType result = Unification.substitute(map, arrayType);
    
    ParsedType expected = new ParsedType.ArrayOf(replacement);
    assertEquals(expected, result);
  }

  @Test
  void substituteAll() throws Exception {
    TypeVariable<?> tv = getTypeVariable();
    ParsedType.Var var = new ParsedType.Var(tv);
    List<ParsedType> types = List.of(
        var,
        new ParsedType.Const(Integer.class),
        new ParsedType.App(new ParsedType.Const(List.class), var)
    );
    ParsedType replacement = new ParsedType.Const(String.class);
    Map<ParsedType.Var, ParsedType> map = Map.of(var, replacement);
    
    List<ParsedType> result = Unification.substituteAll(map, types);
    
    List<ParsedType> expected = List.of(
        replacement,
        new ParsedType.Const(Integer.class),
        new ParsedType.App(new ParsedType.Const(List.class), replacement)
    );
    assertEquals(expected, result);
  }

  // Helper to get a type variable for testing
  private TypeVariable<?> getTypeVariable() throws Exception {
    class TestClass<T> {}
    return TestClass.class.getTypeParameters()[0];
  }
}
