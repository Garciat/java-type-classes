package com.garciat.typeclasses.processor;

import static com.garciat.typeclasses.types.Unit.unit;

import com.garciat.typeclasses.TypeClasses;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.types.Unit;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.tools.Diagnostic;

/**
 * A javac plugin that rewrites witness() calls into direct witness constructor invocations.
 */
public class WitnessResolutionPlugin implements Plugin {
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
              
              // First pass: collect witness calls and their replacements
              Map<JCTree.JCMethodInvocation, JCTree.JCExpression> replacements = new HashMap<>();
              new WitnessCallCollector(trees, astRewriter, replacements)
                  .scan(new TreePath(compilationUnit), null);
              
              // Second pass: apply the replacements
              if (!replacements.isEmpty()) {
                new WitnessReplacer(replacements).translate(compilationUnit);
              }
            }
          }
        });
  }

  /** Scanner that finds witness() calls and computes their replacements. */
  private static class WitnessCallCollector extends TreePathScanner<Void, Void> {
    private final Trees trees;
    private final StaticWitnessSystem system;
    private final AstRewriter astRewriter;
    private final Map<JCTree.JCMethodInvocation, JCTree.JCExpression> replacements;

    private WitnessCallCollector(
        Trees trees,
        AstRewriter astRewriter,
        Map<JCTree.JCMethodInvocation, JCTree.JCExpression> replacements) {
      this.trees = trees;
      this.system = new StaticWitnessSystem();
      this.astRewriter = astRewriter;
      this.replacements = replacements;
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void arg) {
      TreeParser.<MethodInvocationTree>identity()
          .guard(
              TreeParser.<MethodInvocationTree>currentElement()
                  .flatMap(TreeParser.methodMatches(WITNESS_METHOD)))
          .flatMap(TreeParser.unaryCallArgument())
          .flatMap(TreeParser.newAnonymousClassBody())
          .flatMap(TreeParser.singleImplementsClause())
          .flatMap(TreeParser.treeTypeMirror())
          .flatMap(TreeParser.rawTypeMatches(Ty.class))
          .flatMap(TreeParser.unaryTypeArgument())
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
                          plan -> {
                            // On success, compute the replacement
                            try {
                              JCTree.JCExpression replacement =
                                  astRewriter.buildWitnessInvocation(plan);
                              replacements.put((JCTree.JCMethodInvocation) node, replacement);
                            } catch (Exception e) {
                              this.trees.printMessage(
                                  Diagnostic.Kind.WARNING,
                                  "Failed to build replacement: " + e.getMessage(),
                                  getCurrentPath().getLeaf(),
                                  getCurrentPath().getCompilationUnit());
                            }
                            return unit();
                          }));

      return super.visitMethodInvocation(node, arg);
    }
  }

  /** TreeTranslator that applies the replacements. */
  private static class WitnessReplacer extends TreeTranslator {
    private final Map<JCTree.JCMethodInvocation, JCTree.JCExpression> replacements;

    private WitnessReplacer(Map<JCTree.JCMethodInvocation, JCTree.JCExpression> replacements) {
      this.replacements = replacements;
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
      super.visitApply(tree);
      if (replacements.containsKey(tree)) {
        result = replacements.get(tree);
      }
    }
  }
}
