package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.impl.utils.Maybe;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

interface TreeParser<T, R> {
  Maybe<R> parse(Trees trees, TreePath current, T input);

  default <S> TreeParser<T, S> flatMap(TreeParser<R, S> next) {
    return (trees, current, input) ->
        this.parse(trees, current, input).flatMap(r -> next.parse(trees, current, r));
  }

  default <S> TreeParser<T, S> map(Function<R, S> mapper) {
    return flatMap(mapping(mapper));
  }

  default TreeParser<T, R> filter(Predicate<R> predicate) {
    return flatMap(filtering(predicate));
  }

  default <P> TreeParser<T, R> guard(TreeParser<R, P> predicate) {
    return (trees, current, input) ->
        this.parse(trees, current, input)
            .flatMap(r -> predicate.parse(trees, current, r).map(_ -> r));
  }

  static <T> TreeParser<T, T> identity() {
    return (_, _, input) -> Maybe.just(input);
  }

  static <T, R> TreeParser<T, R> mapping(Function<T, R> mapper) {
    return (_, _, input) -> Maybe.just(mapper.apply(input));
  }

  static <T> TreeParser<T, T> filtering(Predicate<T> predicate) {
    return (_, _, input) -> {
      if (predicate.test(input)) {
        return Maybe.just(input);
      } else {
        return Maybe.nothing();
      }
    };
  }

  static <T> TreeParser<T, T> notNull() {
    return filtering(Objects::nonNull);
  }

  static <T, R extends T> TreeParser<T, R> as(Class<R> cls) {
    return (_, _, input) -> {
      if (cls.isInstance(input)) {
        return Maybe.just(cls.cast(input));
      } else {
        return Maybe.nothing();
      }
    };
  }

  static <A> TreeParser<A, Element> currentElement() {
    return (trees, current, _) -> {
      Element element = trees.getElement(current);
      if (element != null) {
        return Maybe.just(element);
      } else {
        return Maybe.nothing();
      }
    };
  }

  static TreeParser<Element, ExecutableElement> methodMatches(Method target) {
    return TreeParser.<Element, ExecutableElement>as(ExecutableElement.class)
        .filter(m -> m.getSimpleName().contentEquals(target.getName()))
        .guard(
            mapping(ExecutableElement::getEnclosingElement)
                .flatMap(as(TypeElement.class))
                .map(TypeElement::getQualifiedName)
                .filter(name -> name.contentEquals(target.getDeclaringClass().getName())));
  }

  static TreeParser<MethodInvocationTree, ExpressionTree> unaryCallArgument() {
    return mapping(MethodInvocationTree::getArguments)
        .filter(list -> list.size() == 1)
        .map(List::getFirst);
  }

  static TreeParser<ExpressionTree, ClassTree> newAnonymousClassBody() {
    return TreeParser.<ExpressionTree, NewClassTree>as(NewClassTree.class)
        .map(NewClassTree::getClassBody)
        .flatMap(notNull());
  }

  static TreeParser<ClassTree, Tree> singleImplementsClause() {
    return mapping(ClassTree::getImplementsClause)
        .flatMap(notNull())
        .filter(list -> list.size() == 1)
        .map(List::getFirst);
  }

  static TreeParser<Tree, TypeMirror> treeTypeMirror() {
    return (trees, current, input) -> {
      try {
        TypeMirror typeMirror =
            trees.getTypeMirror(trees.getPath(current.getCompilationUnit(), input));
        return Maybe.just(typeMirror);
      } catch (IllegalArgumentException e) {
        return Maybe.nothing();
      }
    };
  }

  static TreeParser<TypeMirror, DeclaredType> rawTypeMatches(Class<?> cls) {
    return TreeParser.<TypeMirror, DeclaredType>as(DeclaredType.class)
        .guard(
            declaredTypeElement()
                .flatMap(as(TypeElement.class))
                .map(TypeElement::getQualifiedName)
                .filter(name -> name.contentEquals(cls.getName())));
  }

  static TreeParser<DeclaredType, TypeMirror> unaryTypeArgument() {
    return mapping(DeclaredType::getTypeArguments)
        .filter(list -> list.size() == 1)
        .map(List::getFirst);
  }

  static TreeParser<DeclaredType, Element> declaredTypeElement() {
    return mapping(DeclaredType::asElement).flatMap(notNull());
  }
}
