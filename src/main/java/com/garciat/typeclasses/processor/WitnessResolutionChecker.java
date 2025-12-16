package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.TypeClasses;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.types.Either;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.lang.reflect.Method;
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
      Element element = trees.getElement(getCurrentPath());

      if (isMethodCall(WITNESS_METHOD, element)) {
        // Found a call to TypeClasses.witness()
        // The first argument is expected to be of the form "new Ty<>() {}"
        ExpressionTree firstArg = node.getArguments().getFirst();

        // Check if it's a "new Ty<>() {}" anonymous class creation
        if (firstArg instanceof NewClassTree newClass) {
          Tree tyApp = newClass.getClassBody().getImplementsClause().getFirst();

          TypeMirror typeMirror =
              trees.getTypeMirror(trees.getPath(getCurrentPath().getCompilationUnit(), tyApp));

          // Try to extract witness type and verify resolution
          if (typeMirror instanceof DeclaredType declaredType) {
            TypeMirror witnessTypeMirror = declaredType.getTypeArguments().getFirst();

            ParsedType target = system.parse(witnessTypeMirror);

            switch (WitnessResolution.resolve(system, target)) {
              case Either.Left<
                      WitnessResolution.ResolutionError, WitnessResolution.InstantiationPlan>(
                      var error) ->
                  this.trees.printMessage(
                      Diagnostic.Kind.ERROR,
                      "Failed to resolve witness for type: "
                          + witnessTypeMirror
                          + "\nReason: "
                          + error.format(),
                      getCurrentPath().getLeaf(),
                      getCurrentPath().getCompilationUnit());
              case Either.Right<
                          WitnessResolution.ResolutionError, WitnessResolution.InstantiationPlan>
                      ignore -> {}
            }
          }
        }
      }

      return super.visitMethodInvocation(node, arg);
    }
  }

  private static boolean isMethodCall(Method target, Element element) {
    return element instanceof ExecutableElement method
        && method.getSimpleName().contentEquals(target.getName())
        && method.getEnclosingElement() instanceof TypeElement methodOwner
        && methodOwner.getQualifiedName().contentEquals(target.getDeclaringClass().getName());
  }
}
