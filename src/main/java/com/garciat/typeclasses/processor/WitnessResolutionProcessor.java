package com.garciat.typeclasses.processor;

import com.garciat.typeclasses.impl.ParsedType;
import com.garciat.typeclasses.impl.WitnessResolution;
import com.garciat.typeclasses.types.Either;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.jspecify.annotations.Nullable;

/**
 * Compiler plugin that verifies witness resolution at compile time.
 *
 * <p>This plugin validates that calls to {@code TypeClasses.witness(Ty<T>)} will succeed in terms
 * of witness constructor resolution, according to the resolution rules implemented in the library.
 */
public final class WitnessResolutionProcessor implements Plugin {
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

            Trees trees = Trees.instance(task);
            new WitnessCallScanner(trees).scan(e.getCompilationUnit(), trees);
          }
        });
  }

  /** Scanner that finds calls to TypeClasses.witness() and validates them. */
  private static class WitnessCallScanner extends TreePathScanner<Void, Trees> {
    private final Trees trees;

    WitnessCallScanner(Trees trees) {
      this.trees = trees;
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Trees trees) {
      Element element = trees.getElement(getCurrentPath());

      if (element instanceof ExecutableElement method) {
        Element enclosingElement = method.getEnclosingElement();
        // Check if this is a call to TypeClasses.witness()
        if (method.getSimpleName().toString().equals("witness")
            && enclosingElement != null
            && enclosingElement.toString().equals("com.garciat.typeclasses.TypeClasses")) {

          // Get the type argument from the Ty<T> parameter
          if (!node.getArguments().isEmpty()) {
            var firstArg = node.getArguments().get(0);

            // Check if it's a "new Ty<>() {}" anonymous class creation
            if (firstArg instanceof NewClassTree newClass) {
              var path = trees.getPath(getCurrentPath().getCompilationUnit(), newClass);
              TypeMirror typeMirror = trees.getTypeMirror(path);

              // Try to extract witness type and verify resolution
              verifyWitnessFromTypeMirror(typeMirror, newClass);
            }
          }
        }
      }

      return super.visitMethodInvocation(node, trees);
    }

    private void verifyWitnessFromTypeMirror(TypeMirror typeMirror, NewClassTree node) {
      try {
        // Extract the type argument from Ty<T>
        if (typeMirror instanceof DeclaredType declaredType) {
          var typeArgs = declaredType.getTypeArguments();
          if (!typeArgs.isEmpty()) {
            TypeMirror witnessTypeMirror = typeArgs.get(0);

            // Convert TypeMirror to reflection Type
            java.lang.reflect.@Nullable Type reflectType =
                convertToReflectionType(witnessTypeMirror);

            if (reflectType != null) {
              verifyWitnessResolution(reflectType, node);
            }
          }
        }
      } catch (Exception ex) {
        // If we can't extract or convert the type, skip validation
        // This may happen for complex generic types that can't be resolved at compile time
      }
    }

    private void verifyWitnessResolution(java.lang.reflect.Type type, NewClassTree node) {
      try {
        ParsedType parsed = ParsedType.parse(type);
        Either<WitnessResolution.ResolutionError, WitnessResolution.InstantiationPlan> result =
            WitnessResolution.resolve(parsed, List.of());

        if (result
            instanceof
            Either.Left<WitnessResolution.ResolutionError, WitnessResolution.InstantiationPlan>(
                var error)) {
          String message = "Witness resolution will fail at runtime:\n" + error.format();
          trees.printMessage(
              Diagnostic.Kind.ERROR, message, node, getCurrentPath().getCompilationUnit());
        }
        // If Right, witness resolution will succeed - no error
      } catch (Exception ex) {
        // If parsing or resolution fails unexpectedly, we can't verify - skip
        // This could happen for types that aren't fully resolved yet
      }
    }

    private java.lang.reflect.@Nullable Type convertToReflectionType(TypeMirror typeMirror) {
      try {
        // Get the string representation and try to load the class
        String typeName = typeMirror.toString();

        // Handle parameterized types by extracting the raw type
        int genericStart = typeName.indexOf('<');
        if (genericStart != -1) {
          // For now, we'll try to construct a ParameterizedType
          // This is a simplified approach - a full implementation would need more sophisticated
          // handling
          String rawTypeName = typeName.substring(0, genericStart);
          Class<?> rawType = loadClass(rawTypeName);

          // For simple cases, return the raw type
          // A complete implementation would need to construct proper ParameterizedType instances
          return rawType;
        } else {
          return loadClass(typeName);
        }
      } catch (Exception ex) {
        return null;
      }
    }

    private @Nullable Class<?> loadClass(String name) {
      try {
        // Handle primitive types
        return switch (name) {
          case "int" -> int.class;
          case "long" -> long.class;
          case "short" -> short.class;
          case "byte" -> byte.class;
          case "char" -> char.class;
          case "float" -> float.class;
          case "double" -> double.class;
          case "boolean" -> boolean.class;
          case "void" -> void.class;
          default -> Class.forName(name);
        };
      } catch (ClassNotFoundException ex) {
        return null;
      }
    }
  }
}
