package com.garciat.typeclasses.processor;

import static com.garciat.typeclasses.impl.utils.Streams.isInstanceOf;

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
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
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
          Resolution.Failure<
              ParsedType<TypeVariable, TypeElement, PrimitiveType>, StaticWitnessConstructor>,
          Rose<StaticWitnessConstructor>>
      resolve(TypeMirror target) {
    return Resolution.resolve(
        t -> OverlappingInstances.reduce(findRules(t)),
        (t, c) ->
            Unification.unify(c.returnType(), t)
                .map(map -> Unification.substituteAll(map, c.paramTypes())),
        parse(target));
  }

  private static List<StaticWitnessConstructor> findRules(
      ParsedType<TypeVariable, TypeElement, PrimitiveType> target) {
    return switch (target) {
      case App(var fun, var arg) -> Lists.concat(findRules(fun), findRules(arg));
      case Const(var java) ->
          java.getEnclosedElements().stream()
              .flatMap(isInstanceOf(ExecutableElement.class))
              .flatMap(method -> parseWitnessConstructor(method).stream())
              .toList();
      case Var(_), ArrayOf(_), Primitive(_) -> List.of();
    };
  }

  private static Maybe<StaticWitnessConstructor> parseWitnessConstructor(ExecutableElement method) {
    if (method.getModifiers().contains(Modifier.PUBLIC)
        && method.getModifiers().contains(Modifier.STATIC)
        && method.getAnnotation(TypeClass.Witness.class) instanceof TypeClass.Witness witnessAnn) {
      return Maybe.just(
          new StaticWitnessConstructor(
              method,
              witnessAnn.overlap(),
              method.getParameters().stream()
                  .map(VariableElement::asType)
                  .map(StaticWitnessSystem::parse)
                  .toList(),
              parse(method.getReturnType())));

    } else {
      return Maybe.nothing();
    }
  }

  private static ParsedType<TypeVariable, TypeElement, PrimitiveType> parse(TypeMirror type) {
    return switch (type) {
      case TypeVariable tv -> new Var<>(tv);
      case ArrayType at -> new ArrayOf<>(parse(at.getComponentType()));
      case PrimitiveType pt -> new Primitive<>(pt);
      case DeclaredType dt when parseTagType(dt) instanceof Maybe.Just(var realType) ->
          new Const<>(realType);
      case DeclaredType dt when parseAppType(dt) instanceof Maybe.Just(Pair(var fun, var arg)) ->
          new App<>(parse(fun), parse(arg));
      case DeclaredType dt ->
          dt.getTypeArguments().stream()
              .map(StaticWitnessSystem::parse)
              .reduce(new Const<>(erasure(dt)), App::new);
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
        && tagSuper.getQualifiedName().contentEquals(TagBase.class.getName())) {
      return Maybe.just(enclosing);
    } else {
      return Maybe.nothing();
    }
  }

  private static Maybe<Pair<TypeMirror, TypeMirror>> parseAppType(DeclaredType t) {
    return t.getTypeArguments().size() == 2 && isAppType(erasure(t))
        ? Maybe.just(new Pair<>(t.getTypeArguments().get(0), t.getTypeArguments().get(1)))
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

  public static String format(
      Resolution.Failure<
              ParsedType<TypeVariable, TypeElement, PrimitiveType>, StaticWitnessConstructor>
          error) {
    return Resolution.format(StaticWitnessSystem::format, StaticWitnessSystem::format, error);
  }

  private static String format(ParsedType<TypeVariable, TypeElement, PrimitiveType> ty) {
    return switch (ty) {
      case ParsedType.Var(var v) -> v.toString();
      case ParsedType.Const(var elem) ->
          elem.getSimpleName()
              + elem.getTypeParameters().stream()
                  .map(TypeParameterElement::toString)
                  .reduce((a, b) -> a + ", " + b)
                  .map(s -> "[" + s + "]")
                  .orElse("");
      case ParsedType.App(var fun, var arg) -> format(fun) + "(" + format(arg) + ")";
      case ParsedType.ArrayOf(var elem) -> format(elem) + "[]";
      case ParsedType.Primitive(var prim) -> prim.toString();
    };
  }

  private static String format(StaticWitnessConstructor constructor) {
    return String.format(
        "%s%s -> %s",
        constructor.method().getTypeParameters().stream()
            .map(TypeParameterElement::getSimpleName)
            .map(Name::toString)
            .reduce((a, b) -> a + " " + b)
            .map("∀ %s. "::formatted)
            .orElse(""),
        constructor.paramTypes().stream()
            .map(StaticWitnessSystem::format)
            .collect(Collectors.joining(", ", "(", ")")),
        format(constructor.returnType()));
  }
}
