package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TPar;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.types.Maybe;
import com.garciat.typeclasses.types.Pair;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;

public class RuntimeWitnessSystem {
  public List<WitnessConstructor> findRules(ParsedType target) {
    return switch (target) {
      case ParsedType.App(var fun, var arg) -> Lists.concat(findRules(fun), findRules(arg));
      case ParsedType.Const(var java) ->
          Arrays.stream(java.getDeclaredMethods())
              .flatMap(m -> parseWitnessConstructor(m).stream())
              .toList();
      case ParsedType.Var(var java) -> List.of();
      case ParsedType.ArrayOf(var elem) -> List.of();
      case ParsedType.Primitive(var java) -> List.of();
    };
  }

  private Maybe<WitnessConstructor> parseWitnessConstructor(Method method) {
    if (Modifier.isPublic(method.getModifiers())
        && Modifier.isStatic(method.getModifiers())
        && method.getAnnotation(TypeClass.Witness.class) instanceof TypeClass.Witness witnessAnn) {
      return Maybe.just(
          new WitnessConstructor(
              method,
              witnessAnn.overlap(),
              Arrays.stream(method.getGenericParameterTypes()).map(this::parse).toList(),
              parse(method.getGenericReturnType())));
    } else {
      return Maybe.nothing();
    }
  }

  public ParsedType parse(Type java) {
    return switch (java) {
      case Class<?> tag when parseTagType(tag) instanceof Maybe.Just<Class<?>>(var tagged) ->
          new ParsedType.Const(tagged);
      case Class<?> arr when arr.isArray() -> new ParsedType.ArrayOf(parse(arr.getComponentType()));
      case Class<?> prim when prim.isPrimitive() -> new ParsedType.Primitive(prim);
      case Class<?> c -> new ParsedType.Const(c);
      case TypeVariable<?> v -> new ParsedType.Var(v);
      case ParameterizedType p
          when parseAppType(p)
              instanceof Maybe.Just<Pair<Type, Type>>(Pair<Type, Type>(var fun, var arg)) ->
          new ParsedType.App(parse(fun), parse(arg));
      case ParameterizedType p ->
          Arrays.stream(p.getActualTypeArguments())
              .map(this::parse)
              .reduce(parse(p.getRawType()), ParsedType.App::new);
      case GenericArrayType a -> new ParsedType.ArrayOf(parse(a.getGenericComponentType()));
      case WildcardType w -> throw new IllegalArgumentException("Cannot parse wildcard type: " + w);
      default -> throw new IllegalArgumentException("Unsupported type: " + java);
    };
  }

  private Maybe<Class<?>> parseTagType(Class<?> c) {
    return switch (c.getEnclosingClass()) {
      case Class<?> enclosing when c.getSuperclass().equals(TagBase.class) -> Maybe.just(enclosing);
      case null -> Maybe.nothing();
      default -> Maybe.nothing();
    };
  }

  private Maybe<Pair<Type, Type>> parseAppType(ParameterizedType t) {
    return switch (t.getRawType()) {
      case Class<?> raw when raw.equals(TApp.class) || raw.equals(TPar.class) ->
          Maybe.just(Pair.of(t.getActualTypeArguments()[0], t.getActualTypeArguments()[1]));
      default -> Maybe.nothing();
    };
  }
}
