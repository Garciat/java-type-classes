package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TPar;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.processor.ParsedType.App;
import com.garciat.typeclasses.processor.ParsedType.ArrayOf;
import com.garciat.typeclasses.processor.ParsedType.Const;
import com.garciat.typeclasses.processor.ParsedType.Primitive;
import com.garciat.typeclasses.processor.ParsedType.Var;
import com.garciat.typeclasses.types.Maybe;
import com.garciat.typeclasses.types.Pair;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.*;

public class StaticWitnessSystem {
  private static final Class<?> TAG_BASE_CLASS = TagBase.class;
  private static final Class<?> TAPP_CLASS = TApp.class;
  private static final Class<?> TPAR_CLASS = TPar.class;

  public StaticWitnessSystem() {}

  public List<WitnessConstructor> findRules(ParsedType target) {
    return switch (target) {
      case App(var fun, var arg) -> Lists.concat(findRules(fun), findRules(arg));
      case Const(var java) ->
          java.getEnclosedElements().stream()
              .flatMap(isInstanceOf(ExecutableElement.class))
              .flatMap(method -> parseWitnessConstructor(method).stream())
              .toList();
      case Var(var ignore) -> List.of();
      case ArrayOf(var ignore) -> List.of();
      case Primitive(var ignore) -> List.of();
    };
  }

  private Maybe<WitnessConstructor> parseWitnessConstructor(ExecutableElement method) {
    if (method.getModifiers().contains(Modifier.PUBLIC)
        && method.getModifiers().contains(Modifier.STATIC)
        && method.getAnnotation(TypeClass.Witness.class) instanceof TypeClass.Witness witnessAnn) {
      return Maybe.just(
          new WitnessConstructor(
              method,
              witnessAnn.overlap(),
              method.getParameters().stream()
                  .map(VariableElement::asType)
                  .map(this::parse)
                  .toList(),
              parse(method.getReturnType())));

    } else {
      return Maybe.nothing();
    }
  }

  public ParsedType parse(TypeMirror type) {
    return switch (type) {
      case TypeVariable tv -> new Var(tv);
      case ArrayType at -> new ArrayOf(parse(at.getComponentType()));
      case PrimitiveType pt -> new Primitive(pt);
      case DeclaredType dt when parseTagType(dt) instanceof Maybe.Just<TypeElement>(var realType) ->
          new Const(realType);
      case DeclaredType dt
          when dt.getTypeArguments().isEmpty() && dt.asElement() instanceof TypeElement ty ->
          new Const(ty);
      case DeclaredType dt
          when parseAppType(dt)
              instanceof
              Maybe.Just<Pair<TypeMirror, TypeMirror>>(
                  Pair<TypeMirror, TypeMirror>(var fun, var arg)) ->
          new App(parse(fun), parse(arg));
      case DeclaredType dt ->
          dt.getTypeArguments().stream().map(this::parse).reduce(new Const(erasure(dt)), App::new);
      case WildcardType wt ->
          throw new IllegalArgumentException("Cannot parse wildcard type: " + wt);
      default -> throw new IllegalArgumentException("Unsupported type: " + type);
    };
  }

  private static Maybe<TypeElement> parseTagType(DeclaredType t) {
    if (t.asElement() instanceof TypeElement tag
        && tag.getEnclosingElement() instanceof TypeElement enclosing
        && tag.getSuperclass() instanceof DeclaredType tagSuperType
        && tagSuperType.asElement() instanceof TypeElement tagSuper
        && tagSuper.getQualifiedName().contentEquals(TAG_BASE_CLASS.getName())) {
      return Maybe.just(enclosing);
    } else {
      return Maybe.nothing();
    }
  }

  private Maybe<Pair<TypeMirror, TypeMirror>> parseAppType(DeclaredType t) {
    return t.getTypeArguments().size() == 2 && isAppType(erasure(t))
        ? Maybe.just(new Pair<>(t.getTypeArguments().get(0), t.getTypeArguments().get(1)))
        : Maybe.nothing();
  }

  private boolean isAppType(TypeElement te) {
    return te.getQualifiedName().contentEquals(TAPP_CLASS.getName())
        || te.getQualifiedName().contentEquals(TPAR_CLASS.getName());
  }

  private TypeElement erasure(DeclaredType t) {
    if (t.asElement() instanceof TypeElement te) {
      return te;
    } else {
      throw new IllegalArgumentException("Cannot get erasure of type: " + t);
    }
  }

  private static <T extends U, U> Function<U, Stream<T>> isInstanceOf(Class<T> cls) {
    return u -> cls.isInstance(u) ? Stream.of(cls.cast(u)) : Stream.empty();
  }
}
