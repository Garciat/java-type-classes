package com.garciat.typeclasses.processor;

import static com.garciat.typeclasses.types.Unit.unit;

import com.garciat.typeclasses.TypeClasses;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.types.Unit;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
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
  private AstRewriter astRewriter;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    this.trees = Trees.instance(processingEnv);
    this.astRewriter = new AstRewriter(((com.sun.tools.javac.processing.JavacProcessingEnvironment) processingEnv).getContext(), trees);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element rootElement : roundEnv.getRootElements()) {
      new WitnessCallScanner(trees, astRewriter).scan(trees.getPath(rootElement), null);
    }
    return false;
  }

  /** Scanner that finds calls to TypeClasses.witness() and validates them. */
  private static class WitnessCallScanner extends TreePathScanner<Void, Void> {
    private final Trees trees;
    private final StaticWitnessSystem system;
    private final AstRewriter astRewriter;

    private WitnessCallScanner(Trees trees, AstRewriter astRewriter) {
      this.trees = trees;
      this.system = new StaticWitnessSystem();
      this.astRewriter = astRewriter;
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void arg) {
      // Check if this is a parameterless witness() call
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
     * Handles parameterless witness() calls by resolving the witness type from the expected type
     * and rewriting the AST.
     */
    private void handleParameterlessWitnessCall(MethodInvocationTree node) {
      // First, check if this is a parameterless witness() call
      TreeParser.<MethodInvocationTree>identity()
          .guard(
              TreeParser.<MethodInvocationTree>currentElement()
                  .flatMap(TreeParser.methodMatches(PARAMETERLESS_WITNESS_METHOD)))
          .filter(invocation -> invocation.getArguments().isEmpty())
          .parse(trees, getCurrentPath(), node)
          .fold(
              Unit::unit,
              _ -> {
                // Get the expected type from the context (e.g., variable declaration)
                var expectedType = trees.getTypeMirror(getCurrentPath());
                if (expectedType != null) {
                  var parsedType = system.parse(expectedType);

                  // Resolve the witness
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
                          plan -> {
                            // Success! Rewrite the AST
                            var replacement = astRewriter.buildWitnessExpression(plan);
                            astRewriter.replaceTree(getCurrentPath(), replacement);
                            return unit();
                          });
                }
                return unit();
              });
    }
  }
}
