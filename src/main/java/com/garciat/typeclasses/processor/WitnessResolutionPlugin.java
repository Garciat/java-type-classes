package com.garciat.typeclasses.processor;

import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;

/**
 * A javac plugin that rewrites witness() calls into direct witness constructor invocations.
 */
public class WitnessResolutionPlugin implements Plugin {

  @Override
  public String getName() {
    return "WitnessResolutionPlugin";
  }

  @Override
  public void init(JavacTask task, String... args) {
    Context context = ((BasicJavacTask) task).getContext();
    Trees trees = Trees.instance(task);
    AstRewriter astRewriter = AstRewriter.create(context);

    task.addTaskListener(
        new TaskListener() {
          @Override
          public void finished(TaskEvent event) {
            if (event.getKind() == TaskEvent.Kind.ANALYZE) {
              JCTree.JCCompilationUnit compilationUnit =
                  (JCTree.JCCompilationUnit) event.getCompilationUnit();
              
              // Perform AST rewriting in a single pass
              new WitnessRewriter(trees, astRewriter).translate(compilationUnit);
            }
          }
        });
  }

  /** TreeTranslator that detects and rewrites witness() calls. */
  private static class WitnessRewriter extends TreeTranslator {
    private final Trees trees;
    private final StaticWitnessSystem system;
    private final AstRewriter astRewriter;
    private JCTree.JCCompilationUnit currentCompilationUnit;

    private WitnessRewriter(Trees trees, AstRewriter astRewriter) {
      this.trees = trees;
      this.system = new StaticWitnessSystem();
      this.astRewriter = astRewriter;
    }

    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
      this.currentCompilationUnit = tree;
      super.visitTopLevel(tree);
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
      super.visitApply(tree);
      
      // Check if this is a call to witness()
      if (tree.meth instanceof JCTree.JCFieldAccess methodSelect) {
        if (isWitnessMethod(methodSelect)) {
          tryRewriteWitnessCall(tree, methodSelect);
        }
      } else if (tree.meth instanceof JCTree.JCIdent ident) {
        if (isWitnessMethod(ident)) {
          tryRewriteWitnessCall(tree, null);
        }
      }
    }

    private boolean isWitnessMethod(JCTree.JCFieldAccess methodSelect) {
      String methodName = methodSelect.name.toString();
      return "witness".equals(methodName)
          && methodSelect.selected.toString().endsWith("TypeClasses");
    }

    private boolean isWitnessMethod(JCTree.JCIdent ident) {
      return "witness".equals(ident.name.toString());
    }

    private void tryRewriteWitnessCall(
        JCTree.JCMethodInvocation tree, JCTree.JCFieldAccess methodSelect) {
      try {
        // Determine if this is parameterless or parameterful witness()
        if (tree.args.isEmpty()) {
          // Parameterless witness() - use type from context
          if (tree.type != null) {
            rewriteWithType(tree, tree.type);
          }
        } else if (tree.args.size() == 1) {
          // Could be parameterful witness(Ty<T>) - we keep the existing behavior
          // The annotation processor will validate it
        }
      } catch (Exception e) {
        // Report as a note, not an error, since this is best-effort rewriting
        if (currentCompilationUnit != null) {
          trees.printMessage(
              javax.tools.Diagnostic.Kind.NOTE,
              "Note: Could not rewrite witness call: " + e.getMessage(),
              tree,
              currentCompilationUnit);
        }
      }
    }

    private void rewriteWithType(JCTree.JCMethodInvocation tree, com.sun.tools.javac.code.Type type) {
      try {
        ParsedType parsedType = system.parse(type);
        WitnessResolution.resolve(system, parsedType)
            .fold(
                error -> {
                  // Don't rewrite on error - let the annotation processor report it
                  return null;
                },
                plan -> {
                  JCTree.JCExpression replacement = astRewriter.buildWitnessInvocation(plan);
                  result = replacement;
                  return null;
                });
      } catch (Exception e) {
        // Report as a note for debugging
        if (currentCompilationUnit != null) {
          trees.printMessage(
              javax.tools.Diagnostic.Kind.NOTE,
              "Note: Could not parse type for witness rewriting: " + e.getMessage(),
              tree,
              currentCompilationUnit);
        }
      }
    }
  }
}
