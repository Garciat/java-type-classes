package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TPar;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.impl.utils.Lists;
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
      case ParsedType.App(var fun, var arg) -> Lists.concat(findRules(fun), findRules(arg));
      case ParsedType.Const(var java) ->
          java.asElement().getEnclosedElements().stream()
              .flatMap(isInstanceOf(ExecutableElement.class))
              .flatMap(method -> parseWitnessConstructor(method).stream())
              .toList();
      case ParsedType.Var(var ignore) -> List.of();
      case ParsedType.ArrayOf(var ignore) -> List.of();
      case ParsedType.Primitive(var ignore) -> List.of();
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
      case TypeVariable tv -> new ParsedType.Var(tv);
      case ArrayType at -> new ParsedType.ArrayOf(parse(at.getComponentType()));
      // Store primitive as its boxed type representation, just to have a DeclaredType.
      case PrimitiveType pt -> new ParsedType.Primitive(pt);
      case DeclaredType dt
          when parseTagType(dt) instanceof Maybe.Just<DeclaredType>(var realType) ->
          new ParsedType.Const(realType);
      case DeclaredType dt when dt.getTypeArguments().isEmpty() -> new ParsedType.Const(dt);
      case DeclaredType dt
          when parseAppType(dt)
              instanceof
              Maybe.Just<Pair<TypeMirror, TypeMirror>>(
                  Pair<TypeMirror, TypeMirror>(var fun, var arg)) ->
          new ParsedType.App(parse(fun), parse(arg));
      case DeclaredType dt ->
          dt.getTypeArguments().stream()
              .map(this::parse)
              .reduce(new ParsedType.Const(erasure(dt)), ParsedType.App::new);
      case WildcardType wt ->
          throw new IllegalArgumentException("Cannot parse wildcard type: " + wt);
      default -> throw new IllegalArgumentException("Unsupported type: " + type);
    };
  }

  private static Maybe<DeclaredType> parseTagType(DeclaredType t) {
    if (t.asElement() instanceof TypeElement tag
        && tag.getEnclosingElement() instanceof TypeElement enclosing
        && enclosing.asType() instanceof DeclaredType enclosingType
        && tag.getSuperclass() instanceof DeclaredType tagSuperType
        && tagSuperType.asElement() instanceof TypeElement tagSuper
        && tagSuper.getQualifiedName().contentEquals(TAG_BASE_CLASS.getName())) {
      return Maybe.just(enclosingType);
    } else {
      return Maybe.nothing();
    }
  }

  private Maybe<Pair<TypeMirror, TypeMirror>> parseAppType(DeclaredType t) {
    return t.getTypeArguments().size() == 2 && isAppType(erasure(t))
        ? Maybe.just(new Pair<>(t.getTypeArguments().get(0), t.getTypeArguments().get(1)))
        : Maybe.nothing();
  }

  private boolean isAppType(TypeMirror erasure) {
    return erasure instanceof DeclaredType dt
        && dt.asElement() instanceof TypeElement te
        && (te.getQualifiedName().contentEquals(TAPP_CLASS.getName())
            || te.getQualifiedName().contentEquals(TPAR_CLASS.getName()));
  }

  private DeclaredType erasure(DeclaredType t) {
    return t.asElement().asType() instanceof DeclaredType typeCtor ? typeCtor : t;
  }

  private static <T extends U, U> Function<U, Stream<T>> isInstanceOf(Class<T> cls) {
    return u -> cls.isInstance(u) ? Stream.of(cls.cast(u)) : Stream.empty();
  }
}
