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

  static {
    try {
      WITNESS_METHOD = TypeClasses.class.getMethod("witness", Ty.class);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private Trees trees;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    this.trees = Trees.instance(processingEnv);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element rootElement : roundEnv.getRootElements()) {
      new WitnessCallScanner(trees).scan(trees.getPath(rootElement), null);
    }
    return false;
  }

  /** Scanner that finds calls to TypeClasses.witness() and validates them. */
  private static class WitnessCallScanner extends TreePathScanner<Void, Void> {
    private final Trees trees;

    private WitnessCallScanner(Trees trees) {
      this.trees = trees;
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
                  StaticWitnessSystem.resolve(witnessType)
                      .fold(
                          error -> {
                            this.trees.printMessage(
                                Diagnostic.Kind.ERROR,
                                "Failed to resolve witness for type: "
                                    + witnessType
                                    + "\nReason: "
                                    + StaticWitnessSystem.format(error),
                                getCurrentPath().getLeaf(),
                                getCurrentPath().getCompilationUnit());
                            return unit();
                          },
                          _ -> unit()));

      return super.visitMethodInvocation(node, arg);
    }
  }
}
