package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.processor.WitnessResolution.InstantiationPlan;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import javax.lang.model.element.ExecutableElement;

/** Handles AST rewriting for parameterless witness() calls. */
final class AstRewriter {
  private final TreeMaker treeMaker;
  private final Names names;
  private final Trees trees;

  AstRewriter(Context context, Trees trees) {
    this.treeMaker = TreeMaker.instance(context);
    this.names = Names.instance(context);
    this.trees = trees;
  }

  /**
   * Translates an InstantiationPlan into a JCTree representing the witness constructor call chain.
   */
  JCTree.JCExpression buildWitnessExpression(InstantiationPlan plan) {
    return switch (plan) {
      case InstantiationPlan.PlanStep(var constructor, var dependencies) -> {
        // Get the ExecutableElement for the witness constructor
        ExecutableElement method = constructor.method();

        // Build the method invocation expression
        // Format: ClassName.methodName(dep1, dep2, ...)
        JCTree.JCExpression methodSelect = buildMethodSelect(method);

        // Recursively build expressions for dependencies
        List<JCTree.JCExpression> args =
            List.from(
                dependencies.stream()
                    .map(this::buildWitnessExpression)
                    .toArray(JCTree.JCExpression[]::new));

        // Create the method invocation
        yield treeMaker.Apply(List.nil(), methodSelect, args);
      }
    };
  }

  /**
   * Builds a method select expression for a given executable element. For a static method
   * "ClassName.methodName", this creates the appropriate JCTree.JCFieldAccess.
   */
  private JCTree.JCExpression buildMethodSelect(ExecutableElement method) {
    // Get the enclosing class
    var enclosingElement = method.getEnclosingElement();

    // Build the class reference expression
    JCTree.JCExpression classExpr = buildClassReference(enclosingElement.toString());

    // Create field access: ClassName.methodName
    return treeMaker.Select(classExpr, names.fromString(method.getSimpleName().toString()));
  }

  /**
   * Builds a class reference expression from a fully qualified class name. For example,
   * "com.example.MyClass" becomes a chain of field accesses.
   */
  private JCTree.JCExpression buildClassReference(String qualifiedName) {
    String[] parts = qualifiedName.split("\\.");
    JCTree.JCExpression expr = treeMaker.Ident(names.fromString(parts[0]));

    for (int i = 1; i < parts.length; i++) {
      expr = treeMaker.Select(expr, names.fromString(parts[i]));
    }

    return expr;
  }

  /**
   * Replaces a tree node in the AST. This is the key operation that performs the rewrite.
   *
   * @param path the tree path to the node to replace
   * @param replacement the new tree to use
   */
  void replaceTree(TreePath path, JCTree.JCExpression replacement) {
    Tree leaf = path.getLeaf();
    if (!(leaf instanceof JCTree.JCMethodInvocation originalInvocation)) {
      return;
    }

    // Get parent context
    TreePath parentPath = path.getParentPath();
    if (parentPath == null) {
      return;
    }

    Tree parent = parentPath.getLeaf();

    // We need to replace the method invocation in its parent
    // This is complex and depends on the parent type, so we'll use a simpler approach:
    // Modify the tree in place by replacing fields

    if (parent instanceof JCTree.JCVariableDecl varDecl) {
      // Case: variable declaration
      varDecl.init = replacement;
    } else if (parent instanceof JCTree.JCExpressionStatement exprStmt) {
      // Case: expression statement
      exprStmt.expr = replacement;
    } else if (parent instanceof JCTree.JCReturn returnStmt) {
      // Case: return statement
      returnStmt.expr = replacement;
    } else if (parent instanceof JCTree.JCAssign assign) {
      // Case: assignment on the right side
      if (assign.rhs == originalInvocation) {
        assign.rhs = replacement;
      }
    } else if (parent instanceof JCTree.JCMethodInvocation parentInvocation) {
      // Case: method argument
      List<JCTree.JCExpression> args = parentInvocation.args;
      List<JCTree.JCExpression> newArgs = List.nil();
      for (JCTree.JCExpression arg : args) {
        if (arg == originalInvocation) {
          newArgs = newArgs.append(replacement);
        } else {
          newArgs = newArgs.append(arg);
        }
      }
      parentInvocation.args = newArgs;
    }
    // Add more cases as needed for other parent types
  }
}
