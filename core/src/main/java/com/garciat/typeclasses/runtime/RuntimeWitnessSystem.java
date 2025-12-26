package com.garciat.typeclasses.runtime;

import com.garciat.typeclasses.api.Out;
import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TPar;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.impl.ParsedType;
import com.garciat.typeclasses.impl.ParsedType.App;
import com.garciat.typeclasses.impl.ParsedType.ArrayOf;
import com.garciat.typeclasses.impl.ParsedType.Const;
import com.garciat.typeclasses.impl.ParsedType.Primitive;
import com.garciat.typeclasses.impl.ParsedType.TyParam;
import com.garciat.typeclasses.impl.ParsedType.Var;
import com.garciat.typeclasses.impl.ParsedType.Wildcard;
import com.garciat.typeclasses.impl.Resolution;
import com.garciat.typeclasses.impl.WitnessConstructor;
import com.garciat.typeclasses.impl.utils.Either;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.impl.utils.Maybe;
import com.garciat.typeclasses.impl.utils.Pair;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;

public final class RuntimeWitnessSystem {
  private RuntimeWitnessSystem() {}

  public static Either<
          Resolution.Failure<Runtime.Method, Runtime.Var, Runtime.Const, Runtime.Prim>,
          Resolution.Result<Runtime.Method, Runtime.Var, Runtime.Const, Runtime.Prim>>
      resolve(Type type) {
    return Resolution.resolve(RuntimeWitnessSystem::findWitnesses, parse(type));
  }

  private static List<WitnessConstructor<Runtime.Method, Runtime.Var, Runtime.Const, Runtime.Prim>>
      findWitnesses(Runtime.Const target) {
    return Arrays.stream(target.java().getDeclaredMethods())
        .flatMap(m -> parseWitnessConstructor(m).stream())
        .toList();
  }

  private static Maybe<WitnessConstructor<Runtime.Method, Runtime.Var, Runtime.Const, Runtime.Prim>>
      parseWitnessConstructor(Method method) {
    if (Modifier.isPublic(method.getModifiers())
        && Modifier.isStatic(method.getModifiers())
        && method.getAnnotation(TypeClass.Witness.class) instanceof TypeClass.Witness witnessAnn) {
      return Maybe.just(
          new WitnessConstructor<>(
              new Runtime.Method(method),
              witnessAnn.overlap(),
              typeParams(method),
              Lists.map(method.getGenericParameterTypes(), RuntimeWitnessSystem::parse),
              parse(method.getGenericReturnType())));
    } else {
      return Maybe.nothing();
    }
  }

  public static ParsedType<Runtime.Var, Runtime.Const, Runtime.Prim> parse(Type java) {
    return switch (java) {
      case Class<?> tag when parseTagType(tag) instanceof Maybe.Just(var tagged) ->
          constType(tagged);
      case Class<?> arr when arr.isArray() -> new ArrayOf<>(parse(arr.getComponentType()));
      case Class<?> prim when prim.isPrimitive() -> new Primitive<>(new Runtime.Prim(prim));
      case Class<?> c -> constType(c);
      case ParameterizedType p when parseAppType(p) instanceof Maybe.Just(Pair(var fun, var arg)) ->
          new App<>(parse(fun), parse(arg));
      case ParameterizedType p when parseLazyType(p) instanceof Maybe.Just(var under) ->
          new ParsedType.Lazy<>(parse(under));
      case ParameterizedType p -> {
        Const<Runtime.Var, Runtime.Const, Runtime.Prim> decl = constType((Class<?>) p.getRawType());

        List<ParsedType<Runtime.Var, Runtime.Const, Runtime.Prim>> args =
            Lists.map(p.getActualTypeArguments(), RuntimeWitnessSystem::parse);

        yield Lists.zip(decl.typeParams(), args, TyParam::wrapOut).stream().reduce(decl, App::new);
      }
      case TypeVariable<?> v -> new Var<>(typeParam(v));
      case GenericArrayType a -> new ArrayOf<>(parse(a.getGenericComponentType()));
      case WildcardType _ -> new Wildcard<>();
      default -> throw new IllegalArgumentException("Unsupported type: " + java);
    };
  }

  private static Const<Runtime.Var, Runtime.Const, Runtime.Prim> constType(Class<?> tagged) {
    return new Const<>(new Runtime.Const(tagged), typeParams(tagged));
  }

  private static List<TyParam<Runtime.Var>> typeParams(GenericDeclaration cls) {
    return Lists.map(cls.getTypeParameters(), RuntimeWitnessSystem::typeParam);
  }

  private static TyParam<Runtime.Var> typeParam(TypeVariable<?> t) {
    return new TyParam<>(new Runtime.Var(t), t.isAnnotationPresent(Out.class));
  }

  private static Maybe<Type> parseLazyType(ParameterizedType t) {
    if (erasure(t).equals(com.garciat.typeclasses.api.Lazy.class)
        && t.getActualTypeArguments().length == 1) {
      return Maybe.just(t.getActualTypeArguments()[0]);
    } else {
      return Maybe.nothing();
    }
  }

  private static Maybe<Class<?>> parseTagType(Class<?> c) {
    if (c.getSuperclass() instanceof Class<?> sup
        && sup.equals(TagBase.class)
        && c.getEnclosingClass() instanceof Class<?> enclosing) {
      return Maybe.just(enclosing);
    } else {
      return Maybe.nothing();
    }
  }

  private static Maybe<Pair<Type, Type>> parseAppType(ParameterizedType t) {
    return isAppType(erasure(t)) && t.getActualTypeArguments().length == 2
        ? Maybe.just(Pair.of(t.getActualTypeArguments()[0], t.getActualTypeArguments()[1]))
        : Maybe.nothing();
  }

  private static boolean isAppType(Class<?> t) {
    return t.equals(TApp.class) || t.equals(TPar.class);
  }

  private static Class<?> erasure(ParameterizedType type) {
    return (Class<?>) type.getRawType();
  }
}
