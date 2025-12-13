package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TPar;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.types.Maybe;
import com.garciat.typeclasses.types.Pair;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;

public sealed interface ParsedType {
  record Var(TypeVariable<?> java) implements ParsedType {}

  record App(ParsedType fun, ParsedType arg) implements ParsedType {}

  record ArrayOf(ParsedType elementType) implements ParsedType {}

  record Const(Class<?> java) implements ParsedType {}

  record Primitive(Class<?> java) implements ParsedType {}

  default String format() {
    return switch (this) {
      case Var v -> v.java.getName();
      case Const c ->
          c.java().getSimpleName()
              + Arrays.stream(c.java().getTypeParameters())
                  .map(TypeVariable::getName)
                  .reduce((a, b) -> a + ", " + b)
                  .map(s -> "[" + s + "]")
                  .orElse("");
      case App a -> a.fun.format() + "(" + a.arg.format() + ")";
      case ArrayOf a -> a.elementType.format() + "[]";
      case Primitive p -> p.java().getSimpleName();
    };
  }

  static List<ParsedType> parseAll(Type[] types) {
    return Arrays.stream(types).map(ParsedType::parse).toList();
  }

  static ParsedType parse(Type java) {
    return switch (java) {
      case Class<?> tag when parseTagType(tag) instanceof Maybe.Just<Class<?>>(var tagged) ->
          new Const(tagged);
      case Class<?> arr when arr.isArray() -> new ArrayOf(parse(arr.getComponentType()));
      case Class<?> prim when prim.isPrimitive() -> new Primitive(prim);
      case Class<?> c -> new Const(c);
      case TypeVariable<?> v -> new Var(v);
      case ParameterizedType p
          when parseAppType(p)
              instanceof Maybe.Just<Pair<Type, Type>>(Pair<Type, Type>(var fun, var arg)) ->
          new App(parse(fun), parse(arg));
      case ParameterizedType p ->
          parseAll(p.getActualTypeArguments()).stream().reduce(parse(p.getRawType()), App::new);
      case GenericArrayType a -> new ArrayOf(parse(a.getGenericComponentType()));
      case WildcardType w -> throw new IllegalArgumentException("Cannot parse wildcard type: " + w);
      default -> throw new IllegalArgumentException("Unsupported type: " + java);
    };
  }

  private static Maybe<Class<?>> parseTagType(Class<?> c) {
    return switch (c.getEnclosingClass()) {
      case Class<?> enclosing when c.getSuperclass().equals(TagBase.class) -> Maybe.just(enclosing);
      case null -> Maybe.nothing();
      default -> Maybe.nothing();
    };
  }

  private static Maybe<Pair<Type, Type>> parseAppType(ParameterizedType t) {
    return switch (t.getRawType()) {
      case Class<?> raw when raw.equals(TApp.class) || raw.equals(TPar.class) ->
          Maybe.just(Pair.of(t.getActualTypeArguments()[0], t.getActualTypeArguments()[1]));
      default -> Maybe.nothing();
    };
  }
}
