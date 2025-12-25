package com.garciat.typeclasses.processor;

import static com.garciat.typeclasses.impl.utils.Streams.isInstanceOf;

import com.garciat.typeclasses.api.Lazy;
import com.garciat.typeclasses.api.Out;
import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TPar;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.impl.Match;
import com.garciat.typeclasses.impl.ParsedType;
import com.garciat.typeclasses.impl.ParsedType.App;
import com.garciat.typeclasses.impl.ParsedType.ArrayOf;
import com.garciat.typeclasses.impl.ParsedType.Const;
import com.garciat.typeclasses.impl.ParsedType.Primitive;
import com.garciat.typeclasses.impl.ParsedType.Var;
import com.garciat.typeclasses.impl.ParsedType.Wildcard;
import com.garciat.typeclasses.impl.Resolution;
import com.garciat.typeclasses.impl.WitnessConstructor;
import com.garciat.typeclasses.impl.utils.Either;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.impl.utils.Maybe;
import com.garciat.typeclasses.impl.utils.Pair;
import com.garciat.typeclasses.impl.utils.Rose;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;

public final class StaticWitnessSystem {
  private StaticWitnessSystem() {}

  public static Either<
          Resolution.Failure<Static.Method, Static.Var, Static.Const, Static.Prim>,
          Resolution.Result<Static.Method, Static.Var, Static.Const, Static.Prim>>
      resolve(TypeMirror target) {
    return Resolution.resolve(StaticWitnessSystem::findWitnesses, parse(target));
  }

  private static List<WitnessConstructor<Static.Method, Static.Var, Static.Const, Static.Prim>>
      findWitnesses(Static.Const target) {
    return target.java().getEnclosedElements().stream()
        .flatMap(isInstanceOf(ExecutableElement.class))
        .flatMap(method -> parseWitnessConstructor(method).stream())
        .toList();
  }

  private static Maybe<WitnessConstructor<Static.Method, Static.Var, Static.Const, Static.Prim>>
      parseWitnessConstructor(ExecutableElement method) {
    if (method.getModifiers().contains(Modifier.PUBLIC)
        && method.getModifiers().contains(Modifier.STATIC)
        && method.getAnnotation(TypeClass.Witness.class) instanceof TypeClass.Witness witnessAnn) {
      return Maybe.just(
          new WitnessConstructor<>(
              new Static.Method(method),
              witnessAnn.overlap(),
              typeParams(method),
              Lists.map(method.getParameters(), p -> parse(p.asType())),
              parse(method.getReturnType())));

    } else {
      return Maybe.nothing();
    }
  }

  private static ParsedType<Static.Var, Static.Const, Static.Prim> parse(TypeMirror type) {
    return switch (type) {
      case TypeVariable tv ->
          new Var<>(new Static.Var(tv), tv.asElement().getAnnotation(Out.class) != null);
      case ArrayType at -> new ArrayOf<>(parse(at.getComponentType()));
      case PrimitiveType pt -> new Primitive<>(new Static.Prim(pt));
      case DeclaredType dt when parseTagType(dt) instanceof Maybe.Just(var realType) ->
          constType(realType);
      case DeclaredType dt when parseAppType(dt) instanceof Maybe.Just(Pair(var fun, var arg)) ->
          new App<>(parse(fun), parse(arg));
      case DeclaredType dt when parseLazyType(dt) instanceof Maybe.Just(var under) ->
          new ParsedType.Lazy<>(parse(under));
      case DeclaredType dt ->
          dt.getTypeArguments().stream()
              .map(StaticWitnessSystem::parse)
              .reduce(constType(erasure(dt)), App::new);
      case WildcardType _ -> new Wildcard<>();
      default -> throw new IllegalArgumentException("Unsupported type: " + type);
    };
  }

  private static Const<Static.Var, Static.Const, Static.Prim> constType(TypeElement typeElement) {
    return new Const<>(new Static.Const(typeElement), typeParams(typeElement));
  }

  private static List<Var<Static.Var, Static.Const, Static.Prim>> typeParams(Parameterizable tp) {
    return Lists.map(
        tp.getTypeParameters(),
        tpe ->
            new Var<>(
                new Static.Var((TypeVariable) tpe.asType()), tpe.getAnnotation(Out.class) != null));
  }

  private static Maybe<TypeMirror> parseLazyType(DeclaredType t) {
    if (t.asElement() instanceof TypeElement te
        && te.getQualifiedName().contentEquals(Lazy.class.getName())
        && t.getTypeArguments().size() == 1) {
      return Maybe.just(t.getTypeArguments().getFirst());
    } else {
      return Maybe.nothing();
    }
  }

  private static Maybe<TypeElement> parseTagType(DeclaredType t) {
    if (t.asElement() instanceof TypeElement tag
        && tag.getEnclosingElement() instanceof TypeElement enclosing
        && tag.getSuperclass() instanceof DeclaredType tagSuperType
        && tagSuperType.asElement() instanceof TypeElement tagSuper
        && tagSuper.getQualifiedName().contentEquals(TagBase.class.getName())) {
      return Maybe.just(enclosing);
    } else {
      return Maybe.nothing();
    }
  }

  private static Maybe<Pair<TypeMirror, TypeMirror>> parseAppType(DeclaredType t) {
    return t.getTypeArguments().size() == 2 && isAppType(erasure(t))
        ? Maybe.just(Pair.of(t.getTypeArguments().get(0), t.getTypeArguments().get(1)))
        : Maybe.nothing();
  }

  private static boolean isAppType(TypeElement te) {
    return te.getQualifiedName().contentEquals(TApp.class.getName())
        || te.getQualifiedName().contentEquals(TPar.class.getName());
  }

  private static TypeElement erasure(DeclaredType t) {
    if (t.asElement() instanceof TypeElement te) {
      return te;
    } else {
      throw new IllegalArgumentException("Cannot get erasure of type: " + t);
    }
  }
}
