package com.garciat.typeclasses.processor;

import static com.garciat.typeclasses.types.Unit.unit;

import com.garciat.typeclasses.TypeClasses;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.processor.WitnessResolution.InstantiationPlan;
import com.garciat.typeclasses.types.Unit;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import java.lang.reflect.Method;
import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public final class WitnessResolutionChecker extends AbstractProcessor {
  private static final Method WITNESS_METHOD;
  private static final Method PARAMETERLESS_WITNESS_METHOD;

  static {
    try {
      WITNESS_METHOD = TypeClasses.class.getMethod("witness", Ty.class);
      PARAMETERLESS_WITNESS_METHOD = TypeClasses.class.getMethod("witness");
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private Trees trees;
  private Context context;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.trees = Trees.instance(processingEnv);
    
    // Get the JavacTask and Context for AST rewriting
    if (processingEnv instanceof com.sun.tools.javac.processing.JavacProcessingEnvironment javacEnv) {
      this.context = javacEnv.getContext();
      
      // Try to get JavacTask from the context using different approaches
      try {
        // First try: Get BasicJavacTask directly
        BasicJavacTask task = context.get(BasicJavacTask.class);
        if (task != null) {
          task.addTaskListener(new AstTransformListener());
        } else {
          // Second try: Get JavacTaskImpl
          com.sun.tools.javac.api.JavacTaskImpl taskImpl = context.get(com.sun.tools.javac.api.JavacTaskImpl.class);
          if (taskImpl != null) {
            taskImpl.addTaskListener(new AstTransformListener());
          }
        }
      } catch (Exception e) {
        // Silent failure - AST rewriting won't work but validation will still happen
      }
    }
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element rootElement : roundEnv.getRootElements()) {
      new WitnessCallScanner(trees).scan(trees.getPath(rootElement), null);
    }
    return false;
  }

  /**
   * TaskListener that runs after ANALYZE phase to perform AST transformations.
   */
  private class AstTransformListener implements TaskListener {
    @Override
    public void finished(TaskEvent e) {
      if (e.getKind() == TaskEvent.Kind.ANALYZE) {
        // Perform AST transformation after type attribution is complete
        if (e.getCompilationUnit() != null && context != null) {
          JCTree.JCCompilationUnit cu = (JCTree.JCCompilationUnit) e.getCompilationUnit();
          cu.accept(new WitnessCallTranslator(context, trees));
        }
      }
    }
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
      // Check if this is a parameterless witness() call - validate it
      handleParameterlessWitnessCall(node);

      // Check if this is a witness(Ty) call
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
                          _ -> unit()));

      return super.visitMethodInvocation(node, arg);
    }

    /**
     * Validates parameterless witness() calls.
     */
    private void handleParameterlessWitnessCall(MethodInvocationTree node) {
      TreeParser.<MethodInvocationTree>identity()
          .guard(
              TreeParser.<MethodInvocationTree>currentElement()
                  .flatMap(TreeParser.methodMatches(PARAMETERLESS_WITNESS_METHOD)))
          .filter(invocation -> invocation.getArguments().isEmpty())
          .parse(trees, getCurrentPath(), node)
          .fold(
              Unit::unit,
              _ -> {
                // Get the expected type from the context
                var expectedType = trees.getTypeMirror(getCurrentPath());
                if (expectedType != null) {
                  var parsedType = system.parse(expectedType);

                  // Validate that witness can be resolved
                  WitnessResolution.resolve(system, parsedType)
                      .fold(
                          error -> {
                            this.trees.printMessage(
                                Diagnostic.Kind.ERROR,
                                "Failed to resolve witness for parameterless witness() call with type: "
                                    + expectedType
                                    + "\nReason: "
                                    + error.format(),
                                getCurrentPath().getLeaf(),
                                getCurrentPath().getCompilationUnit());
                            return unit();
                          },
                          plan -> unit());
                }
                return unit();
              });
    }
  }

  /**
   * Tree translator that rewrites parameterless witness() calls.
   */
  private static class WitnessCallTranslator extends TreeTranslator {
    private final AstRewriter astRewriter;
    private final StaticWitnessSystem system;
    private final Trees trees;

    private WitnessCallTranslator(Context context, Trees trees) {
      this.astRewriter = new AstRewriter(context, trees);
      this.system = new StaticWitnessSystem();
      this.trees = trees;
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
      // First visit the method expression and arguments
      tree.meth = translate(tree.meth);
      tree.args = translate(tree.args);
      tree.typeargs = translate(tree.typeargs);
      
      // Now check if this is a call to the parameterless witness() method
      if (tree.meth instanceof JCTree.JCFieldAccess fieldAccess) {
        if (fieldAccess.sym instanceof Symbol.MethodSymbol methodSymbol) {
          // Check if it matches our parameterless witness method
          if (methodSymbol.getSimpleName().toString().equals("witness") 
              && methodSymbol.params().isEmpty()
              && methodSymbol.owner.toString().equals(TypeClasses.class.getName())) {
            
            // This is a parameterless witness() call - try to rewrite it
            if (tree.type != null) {
              var parsedType = system.parse(tree.type);
              WitnessResolution.resolve(system, parsedType)
                  .fold(
                      error -> null, // Validation phase already reported the error
                      plan -> {
                        // Build the replacement expression
                        JCTree.JCExpression replacement = astRewriter.buildWitnessExpression(plan);
                        // Set the result to replace this node
                        result = replacement;
                        return null;
                      });
            }
          }
        }
      }
      
      // If we didn't replace it, keep the original
      if (result == null) {
        result = tree;
      }
    }
  }
}
