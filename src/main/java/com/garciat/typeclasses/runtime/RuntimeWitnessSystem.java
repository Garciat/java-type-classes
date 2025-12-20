package com.garciat.typeclasses.runtime;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TPar;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.impl.OverlappingInstances;
import com.garciat.typeclasses.impl.ParsedType;
import com.garciat.typeclasses.impl.ParsedType.App;
import com.garciat.typeclasses.impl.ParsedType.ArrayOf;
import com.garciat.typeclasses.impl.ParsedType.Const;
import com.garciat.typeclasses.impl.ParsedType.Primitive;
import com.garciat.typeclasses.impl.ParsedType.Var;
import com.garciat.typeclasses.impl.Resolution;
import com.garciat.typeclasses.impl.Unification;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.impl.utils.Rose;
import com.garciat.typeclasses.types.Either;
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

public final class RuntimeWitnessSystem {
  private RuntimeWitnessSystem() {}

  public static Either<
          Resolution.Failure<
              ParsedType<TypeVariable<?>, Class<?>, Class<?>>, RuntimeWitnessConstructor>,
          Rose<RuntimeWitnessConstructor>>
      resolve(Type type) {
    return Resolution.resolve(
        t -> OverlappingInstances.reduce(findRules(t)),
        (t, c) ->
            Unification.unify(c.returnType(), t)
                .map(map -> Unification.substituteAll(map, c.paramTypes())),
        parse(type));
  }

  private static List<RuntimeWitnessConstructor> findRules(
      ParsedType<TypeVariable<?>, Class<?>, Class<?>> target) {
    return switch (target) {
      case App(var fun, var arg) -> Lists.concat(findRules(fun), findRules(arg));
      case Const(var java) ->
          Arrays.stream(java.getDeclaredMethods())
              .flatMap(m -> parseWitnessConstructor(m).stream())
              .toList();
      case Var(_), ArrayOf(_), Primitive(_) -> List.of();
    };
  }

  private static Maybe<RuntimeWitnessConstructor> parseWitnessConstructor(Method method) {
    if (Modifier.isPublic(method.getModifiers())
        && Modifier.isStatic(method.getModifiers())
        && method.getAnnotation(TypeClass.Witness.class) instanceof TypeClass.Witness witnessAnn) {
      return Maybe.just(
          new RuntimeWitnessConstructor(
              method,
              witnessAnn.overlap(),
              Arrays.stream(method.getGenericParameterTypes())
                  .map(RuntimeWitnessSystem::parse)
                  .toList(),
              parse(method.getGenericReturnType())));
    } else {
      return Maybe.nothing();
    }
  }

  private static ParsedType<TypeVariable<?>, Class<?>, Class<?>> parse(Type java) {
    return switch (java) {
      case Class<?> tag when parseTagType(tag) instanceof Maybe.Just(var tagged) ->
          new Const<>(tagged);
      case Class<?> arr when arr.isArray() -> new ArrayOf<>(parse(arr.getComponentType()));
      case Class<?> prim when prim.isPrimitive() -> new Primitive<>(prim);
      case Class<?> c -> new Const<>(c);
      case TypeVariable<?> v -> new Var<>(v);
      case ParameterizedType p when parseAppType(p) instanceof Maybe.Just(Pair(var fun, var arg)) ->
          new App<>(parse(fun), parse(arg));
      case ParameterizedType p ->
          Arrays.stream(p.getActualTypeArguments())
              .map(RuntimeWitnessSystem::parse)
              .reduce(parse(p.getRawType()), App::new);
      case GenericArrayType a -> new ArrayOf<>(parse(a.getGenericComponentType()));
      case WildcardType w -> throw new IllegalArgumentException("Cannot parse wildcard type: " + w);
      default -> throw new IllegalArgumentException("Unsupported type: " + java);
    };
  }

  private static Maybe<Class<?>> parseTagType(Class<?> c) {
    return switch (c.getEnclosingClass()) {
      case Class<?> enclosing when c.getSuperclass().equals(TagBase.class) -> Maybe.just(enclosing);
      case null, default -> Maybe.nothing();
    };
  }

  private static Maybe<Pair<Type, Type>> parseAppType(ParameterizedType t) {
    return switch (t.getRawType()) {
      case Class<?> raw when raw.equals(TApp.class) || raw.equals(TPar.class) ->
          Maybe.just(Pair.of(t.getActualTypeArguments()[0], t.getActualTypeArguments()[1]));
      default -> Maybe.nothing();
    };
  }

  public static String format(ParsedType<TypeVariable<?>, Class<?>, Class<?>> ty) {
    return switch (ty) {
      case Var(var v) -> v.getName();
      case Const(var cls) ->
          cls.getSimpleName()
              + Arrays.stream(cls.getTypeParameters())
                  .map(TypeVariable::getName)
                  .reduce((a, b) -> a + ", " + b)
                  .map(s -> "[" + s + "]")
                  .orElse("");
      case App(var fun, var arg) -> format(fun) + "(" + format(arg) + ")";
      case ArrayOf(var elem) -> format(elem) + "[]";
      case Primitive(var prim) -> prim.getSimpleName();
    };
  }

  public static String format(
      Resolution.Failure<ParsedType<TypeVariable<?>, Class<?>, Class<?>>, RuntimeWitnessConstructor>
          error) {
    return Resolution.format(
        RuntimeWitnessSystem::format, RuntimeWitnessConstructor::format, error);
  }
}
