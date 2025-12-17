package com.garciat.typeclasses.processor;

import static com.garciat.typeclasses.types.Unit.unit;

import com.garciat.typeclasses.TypeClasses;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.types.Maybe;
import com.garciat.typeclasses.types.Unit;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
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
import javax.tools.Diagnostic;

public final class WitnessResolutionChecker implements Plugin {
  private static final Method WITNESS_METHOD;

  static {
    try {
      WITNESS_METHOD = TypeClasses.class.getMethod("witness", Ty.class);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getName() {
    return "WitnessResolutionChecker";
  }

  @Override
  public void init(JavacTask task, String... args) {
    task.addTaskListener(
        new TaskListener() {
          @Override
          public void finished(TaskEvent e) {
            if (e.getKind() != TaskEvent.Kind.ANALYZE) {
              return;
            }

            if (e.getCompilationUnit() == null) {
              return;
            }

            new WitnessCallScanner(Trees.instance(task)).scan(e.getCompilationUnit(), null);
          }
        });
  }

  /** Scanner that finds calls to TypeClasses.witness() and validates them. */
  private static class WitnessCallScanner extends TreePathScanner<Void, Void> {
    private final Trees trees;
    private final StaticWitnessSystem system;

    private WitnessCallScanner(Trees trees) {
      this.trees = trees;
      this.system = new StaticWitnessSystem();
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void arg) {
      Parser.<MethodInvocationTree>identity()
          .guard(
              Parser.<MethodInvocationTree>currentElement()
                  .flatMap(Parser.methodMatches(WITNESS_METHOD)))
          .flatMap(Parser.unaryCallArgument())
          .flatMap(Parser.newAnonymousClassBody())
          .flatMap(Parser.singleImplementsClause())
          .flatMap(Parser.treeTypeMirror())
          .flatMap(Parser.rawTypeMatches(Ty.class))
          .flatMap(Parser.unaryTypeArgument())
          .parse(trees, getCurrentPath(), node)
          .fold(
              Unit::unit,
              witnessType ->
                  WitnessResolution.resolve(system, system.parse(witnessType))
                      .fold(
                          error -> {
                            this.trees.printMessage(
                                Diagnostic.Kind.ERROR,
                                "Failed to resolve witness for type: "
                                    + witnessType
                                    + "\nReason: "
                                    + error.format(),
                                getCurrentPath().getLeaf(),
                                getCurrentPath().getCompilationUnit());
                            return unit();
                          },
                          plan -> unit()));

      return super.visitMethodInvocation(node, arg);
    }
  }
}

interface Parser<T, R> {
  Maybe<R> parse(Trees trees, TreePath current, T input);

  default <S> Parser<T, S> flatMap(Parser<R, S> next) {
    return (trees, current, input) ->
        this.parse(trees, current, input).flatMap(r -> next.parse(trees, current, r));
  }

  default <S> Parser<T, S> map(Function<R, S> mapper) {
    return flatMap(mapping(mapper));
  }

  default Parser<T, R> filter(Predicate<R> predicate) {
    return flatMap(filtering(predicate));
  }

  default Parser<T, R> guard(Parser<R, ?> predicate) {
    return (trees, current, input) ->
        this.parse(trees, current, input)
            .flatMap(r -> predicate.parse(trees, current, r).map(x -> r));
  }

  static <T> Parser<T, T> identity() {
    return (trees, current, input) -> Maybe.just(input);
  }

  static <T, R> Parser<T, R> mapping(Function<T, R> mapper) {
    return (trees, current, input) -> Maybe.just(mapper.apply(input));
  }

  static <T> Parser<T, T> filtering(Predicate<T> predicate) {
    return (trees, current, input) -> {
      if (predicate.test(input)) {
        return Maybe.just(input);
      } else {
        return Maybe.nothing();
      }
    };
  }

  static <T> Parser<T, T> notNull() {
    return filtering(Objects::nonNull);
  }

  static <T, R extends T> Parser<T, R> as(Class<R> cls) {
    return (trees, current, input) -> {
      if (cls.isInstance(input)) {
        return Maybe.just(cls.cast(input));
      } else {
        return Maybe.nothing();
      }
    };
  }

  static <A> Parser<A, Element> currentElement() {
    return (trees, current, input) -> {
      Element element = trees.getElement(current);
      if (element != null) {
        return Maybe.just(element);
      } else {
        return Maybe.nothing();
      }
    };
  }

  static Parser<Element, ExecutableElement> methodMatches(Method target) {
    return Parser.<Element, ExecutableElement>as(ExecutableElement.class)
        .filter(m -> m.getSimpleName().contentEquals(target.getName()))
        .guard(
            mapping(ExecutableElement::getEnclosingElement)
                .flatMap(as(TypeElement.class))
                .map(TypeElement::getQualifiedName)
                .filter(name -> name.contentEquals(target.getDeclaringClass().getName())));
  }

  static Parser<MethodInvocationTree, ExpressionTree> unaryCallArgument() {
    return mapping(MethodInvocationTree::getArguments)
        .filter(list -> list.size() == 1)
        .map(List::getFirst);
  }

  static Parser<ExpressionTree, ClassTree> newAnonymousClassBody() {
    return Parser.<ExpressionTree, NewClassTree>as(NewClassTree.class)
        .map(NewClassTree::getClassBody)
        .flatMap(notNull());
  }

  static Parser<ClassTree, Tree> singleImplementsClause() {
    return mapping(ClassTree::getImplementsClause)
        .flatMap(notNull())
        .filter(list -> list.size() == 1)
        .map(List::getFirst);
  }

  static Parser<Tree, TypeMirror> treeTypeMirror() {
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

  static Parser<TypeMirror, DeclaredType> rawTypeMatches(Class<?> cls) {
    return Parser.<TypeMirror, DeclaredType>as(DeclaredType.class)
        .guard(
            declaredTypeElement()
                .flatMap(as(TypeElement.class))
                .map(TypeElement::getQualifiedName)
                .filter(name -> name.contentEquals(cls.getName())));
  }

  static Parser<DeclaredType, TypeMirror> unaryTypeArgument() {
    return mapping(DeclaredType::getTypeArguments)
        .filter(list -> list.size() == 1)
        .map(List::getFirst);
  }

  static Parser<DeclaredType, Element> declaredTypeElement() {
    return mapping(DeclaredType::asElement).flatMap(notNull());
  }
}
