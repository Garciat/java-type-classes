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
      Parser.unaryMethodCallArgument(WITNESS_METHOD)
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

  static <T> Parser<MethodInvocationTree, ExpressionTree> unaryMethodCallArgument(Method target) {
    return (trees, current, input) -> {
      if (trees.getElement(current) instanceof ExecutableElement method
          && method.getSimpleName().contentEquals(target.getName())
          && method.getEnclosingElement() instanceof TypeElement methodOwner
          && methodOwner.getQualifiedName().contentEquals(target.getDeclaringClass().getName())
          && input.getArguments().size() == 1) {
        return Maybe.just(input.getArguments().getFirst());
      } else {
        return Maybe.nothing();
      }
    };
  }

  static Parser<ExpressionTree, ClassTree> newAnonymousClassBody() {
    return (trees, current, input) -> {
      if (input instanceof NewClassTree newClass && newClass.getClassBody() != null) {
        return Maybe.just(newClass.getClassBody());
      } else {
        return Maybe.nothing();
      }
    };
  }

  static Parser<ClassTree, Tree> singleImplementsClause() {
    return (trees, current, input) -> {
      if (input.getImplementsClause() != null && input.getImplementsClause().size() == 1) {
        return Maybe.just(input.getImplementsClause().getFirst());
      } else {
        return Maybe.nothing();
      }
    };
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
    return (trees, current, input) -> {
      if (input instanceof DeclaredType declaredType
          && declaredType.asElement() instanceof TypeElement typeElement
          && typeElement.getQualifiedName().contentEquals(cls.getName())) {
        return Maybe.just(declaredType);
      }
      return Maybe.nothing();
    };
  }

  static Parser<DeclaredType, TypeMirror> unaryTypeArgument() {
    return (trees, current, input) -> {
      if (input.getTypeArguments().size() == 1) {
        return Maybe.just(input.getTypeArguments().getFirst());
      } else {
        return Maybe.nothing();
      }
    };
  }
}
