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
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
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
    Context context = ((com.sun.tools.javac.api.BasicJavacTask) task).getContext();

    task.addTaskListener(
        new TaskListener() {
          @Override
          public void finished(TaskEvent e) {
            if (e.getKind() == TaskEvent.Kind.ANALYZE) {
              // Transform immediately after ANALYZE when type info is available
              if (e.getCompilationUnit() != null) {
                new WitnessCallRewriter(Trees.instance(task), context)
                    .transform((JCTree.JCCompilationUnit) e.getCompilationUnit());
              }
            }
          }
        });
  }

  /** Rewriter that finds calls to TypeClasses.witness() and rewrites them with actual calls. */
  @SuppressWarnings("NullAway")
  private static class WitnessCallRewriter extends com.sun.tools.javac.tree.TreeTranslator {
    private final Trees trees;
    private final StaticWitnessSystem system;
    private final TreeMaker treeMaker;
    private final Names names;
    private final Attr attr;
    private final Resolve resolve;
    private final Types types;
    private final Symtab symtab;
    private JCTree.JCCompilationUnit currentCompilationUnit;
    private Env<AttrContext> env;

    private WitnessCallRewriter(Trees trees, Context context) {
      this.trees = trees;
      this.system = new StaticWitnessSystem();
      this.treeMaker = TreeMaker.instance(context);
      this.names = Names.instance(context);
      this.attr = Attr.instance(context);
      this.resolve = Resolve.instance(context);
      this.types = Types.instance(context);
      this.symtab = Symtab.instance(context);
    }

    /** Transform the compilation unit by replacing witness() calls. */
    public void transform(JCTree.JCCompilationUnit compilationUnit) {
      this.currentCompilationUnit = compilationUnit;
      // First scan to validate witness calls
      new ValidationScanner().scan(compilationUnit, null);
      // Then apply transformations
      compilationUnit.defs = translate(compilationUnit.defs);
    }

    /** Scanner to validate witness calls before transformation. */
    private class ValidationScanner extends com.sun.source.util.TreePathScanner<Void, Void> {
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
                              trees.printMessage(
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

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
      // Try to transform witness() calls with proper attribution
      TreePath path = trees.getPath(currentCompilationUnit, tree);
      if (path != null) {
        Maybe<TypeMirror> witnessType =
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
                .parse(trees, path, tree);

        witnessType.fold(
            Unit::unit,
            wt -> {
              WitnessResolution.resolve(system, system.parse(wt))
                  .fold(
                      error -> unit(), // Error already reported in validation
                      plan -> {
                        try {
                          // Build the replacement tree
                          JCTree.JCExpression replacement = buildInstantiationTree(plan);
                          if (replacement != null) {
                            replacement.pos = tree.pos;

                            // Try to resolve and attribute the replacement
                            // This is the key: we need to attribute the new tree
                            if (tree.type != null) {
                              // Attempt to set the type on the replacement
                              replacement.type = tree.type;
                            }

                            result = replacement;
                          }
                        } catch (Exception e) {
                          // If transformation fails, keep original
                          result = tree;
                        }
                        return unit();
                      });
              return unit();
            });
      }

      // Only call super if we didn't transform
      if (result == null || result == tree) {
        super.visitApply(tree);
      }
    }

    /** Recursively builds a JCTree from an InstantiationPlan with proper type attribution. */
    private JCTree.JCExpression buildInstantiationTree(WitnessResolution.InstantiationPlan plan) {
      return switch (plan) {
        case WitnessResolution.InstantiationPlan.PlanStep(var constructor, var dependencies) -> {
          // Get the ExecutableElement for the witness constructor
          ExecutableElement method = constructor.method();

          // Build the method reference
          JCTree.JCExpression methodSelect = buildMethodReference(method);

          // Build the arguments by recursively processing dependencies
          com.sun.tools.javac.util.List<JCTree.JCExpression> args =
              com.sun.tools.javac.util.List.from(
                  dependencies.stream().map(this::buildInstantiationTree).toList());

          // Create the method invocation
          JCTree.JCMethodInvocation methodInvocation =
              treeMaker.Apply(com.sun.tools.javac.util.List.nil(), methodSelect, args);

          // Set the type on the method invocation
          // The return type of the witness constructor method
          if (method instanceof Symbol.MethodSymbol methodSymbol) {
            methodInvocation.type = methodSymbol.getReturnType();
          } else if (method.getReturnType() instanceof javax.lang.model.type.DeclaredType dt) {
            // Try to get the type from the ExecutableElement
            TypeMirror returnType = method.getReturnType();
            if (returnType instanceof Type jcType) {
              methodInvocation.type = jcType;
            }
          }

          yield methodInvocation;
        }
      };
    }

    /**
     * Builds a JCTree expression that references the given method with proper symbol information.
     */
    private JCTree.JCExpression buildMethodReference(ExecutableElement method) {
      // Get the enclosing class
      Element enclosingElement = method.getEnclosingElement();

      if (enclosingElement instanceof TypeElement typeElement) {
        // Build the class name expression
        JCTree.JCExpression classExpr = buildQualifiedName(typeElement);

        // Build the method name
        com.sun.tools.javac.util.Name methodName =
            names.fromString(method.getSimpleName().toString());

        // Create the field access (ClassName.methodName)
        JCTree.JCFieldAccess fieldAccess = treeMaker.Select(classExpr, methodName);

        // Set the symbol if method is a Symbol.MethodSymbol
        if (method instanceof Symbol.MethodSymbol methodSymbol) {
          fieldAccess.sym = methodSymbol;
          fieldAccess.type = methodSymbol.type;
        }

        return fieldAccess;
      } else {
        throw new IllegalArgumentException(
            "Method does not have a TypeElement as enclosing element: " + method);
      }
    }

    /** Builds a qualified name expression with proper symbol and type information. */
    private JCTree.JCExpression buildQualifiedName(TypeElement typeElement) {
      String qualifiedName = typeElement.getQualifiedName().toString();
      String[] parts = qualifiedName.split("\\.");

      JCTree.JCExpression expr = treeMaker.Ident(names.fromString(parts[0]));

      // Set symbol and type if typeElement is a Symbol.ClassSymbol
      if (typeElement instanceof Symbol.ClassSymbol classSymbol) {
        // For the final expression, set the class symbol
        Symbol currentSym = classSymbol;

        // Navigate through package symbols to build the path
        for (int i = parts.length - 2; i >= 0; i--) {
          if (currentSym.owner != null) {
            currentSym = currentSym.owner;
          }
        }

        // Build the expression with proper types
        if (parts.length == 1) {
          if (expr instanceof JCTree.JCIdent ident) {
            ident.sym = classSymbol;
            ident.type = classSymbol.type;
          }
        } else {
          Symbol pkgSym = currentSym;
          if (expr instanceof JCTree.JCIdent ident && pkgSym instanceof Symbol.PackageSymbol) {
            ident.sym = pkgSym;
            ident.type = pkgSym.type;
          }

          for (int i = 1; i < parts.length; i++) {
            JCTree.JCFieldAccess select = treeMaker.Select(expr, names.fromString(parts[i]));
            if (i == parts.length - 1) {
              select.sym = classSymbol;
              select.type = classSymbol.type;
            }
            expr = select;
          }
        }
      } else {
        // Fallback: just build the structure without symbols
        for (int i = 1; i < parts.length; i++) {
          expr = treeMaker.Select(expr, names.fromString(parts[i]));
        }
      }

      return expr;
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
