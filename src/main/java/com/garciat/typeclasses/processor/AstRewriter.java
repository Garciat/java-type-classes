package com.garciat.typeclasses.processor;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Handles AST rewriting to replace witness() calls with direct witness constructor invocations.
 */
public final class AstRewriter {
  private final TreeMaker treeMaker;
  private final Names names;

  private AstRewriter(TreeMaker treeMaker, Names names) {
    this.treeMaker = treeMaker;
    this.names = names;
  }

  /**
   * Creates an AstRewriter instance from a Context.
   *
   * @param context The javac Context
   * @return A new AstRewriter instance
   */
  public static AstRewriter create(Context context) {
    TreeMaker treeMaker = TreeMaker.instance(context);
    Names names = Names.instance(context);
    return new AstRewriter(treeMaker, names);
  }

  /**
   * Translates an InstantiationPlan tree into a JCTree of method call invocations.
   *
   * @param plan The InstantiationPlan to translate
   * @return A JCTree.JCExpression representing the method calls
   */
  public JCTree.JCExpression buildWitnessInvocation(
      WitnessResolution.InstantiationPlan plan) {
    return buildWitnessInvocationRecursive(plan);
  }

  private JCTree.JCExpression buildWitnessInvocationRecursive(
      WitnessResolution.InstantiationPlan plan) {
    if (plan instanceof WitnessResolution.InstantiationPlan.PlanStep step) {
      WitnessConstructor constructor = step.target();
      ExecutableElement method = constructor.method();

      // Get the enclosing class
      TypeElement enclosingClass = (TypeElement) method.getEnclosingElement();

      // Build the class reference (e.g., MyClass)
      JCTree.JCExpression classRef = buildClassReference(enclosingClass);

      // Build the method select (e.g., MyClass.witnessMethod)
      JCTree.JCFieldAccess methodSelect =
          treeMaker.Select(classRef, names.fromString(method.getSimpleName().toString()));

      // Recursively build arguments from dependencies
      com.sun.tools.javac.util.List<JCTree.JCExpression> args =
          com.sun.tools.javac.util.List.from(
              step.dependencies().stream()
                  .map(this::buildWitnessInvocationRecursive)
                  .collect(java.util.stream.Collectors.toList()));

      // Build the method invocation
      return treeMaker.Apply(List.nil(), methodSelect, args);
    }
    throw new IllegalArgumentException("Unexpected InstantiationPlan type: " + plan);
  }

  private JCTree.JCExpression buildClassReference(TypeElement typeElement) {
    String qualifiedName = typeElement.getQualifiedName().toString();
    String[] parts = qualifiedName.split("\\.");

    JCTree.JCExpression expr = treeMaker.Ident(names.fromString(parts[0]));
    for (int i = 1; i < parts.length; i++) {
      expr = treeMaker.Select(expr, names.fromString(parts[i]));
    }
    return expr;
  }
}
