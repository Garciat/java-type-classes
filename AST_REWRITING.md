# AST Rewriting Prototype for Parameterless witness()

## Overview

This prototype implements the infrastructure for rewriting `TypeClasses.witness(new Ty<>() {})` calls into direct witness constructor invocations during compilation. The implementation demonstrates how to translate an `InstantiationPlan` into JCTree AST nodes that represent the actual method calls needed to construct a witness.

## Implementation

The implementation consists of several key components:

###  1. WitnessCallRewriter (TreeTranslator)

Located in `WitnessResolutionChecker.java`, this class extends `TreeTranslator` to traverse and potentially modify the AST:

- **visitApply()**: Intercepts method invocation nodes and checks if they are calls to `TypeClasses.witness()`
- **buildInstantiationTree()**: Recursively constructs JCTree nodes from an `InstantiationPlan`
- **buildMethodReference()**: Creates qualified name expressions for witness constructor methods
- **buildQualifiedName()**: Builds fully qualified class name expressions

### 2. InstantiationPlan to JCTree Translation

The `buildInstantiationTree()` method shows how to translate the witness resolution plan into actual Java AST nodes:

```java
private JCTree.JCExpression buildInstantiationTree(WitnessResolution.InstantiationPlan plan) {
  return switch (plan) {
    case WitnessResolution.InstantiationPlan.PlanStep(var constructor, var dependencies) -> {
      // Get the ExecutableElement for the witness constructor
      ExecutableElement method = constructor.method();
      
      // Build the method reference (e.g., ClassName.methodName)
      JCTree.JCExpression methodSelect = buildMethodReference(method);
      
      // Recursively build arguments from dependencies
      com.sun.tools.javac.util.List<JCTree.JCExpression> args =
          com.sun.tools.javac.util.List.from(
              dependencies.stream().map(this::buildInstantiationTree).toList());
      
      // Create the method invocation
      yield treeMaker.Apply(com.sun.tools.javac.util.List.nil(), methodSelect, args);
    }
  };
}
```

### 3. Example Transformation

Given this witness call:
```java
Show<int[]> show = witness(new Ty<>() {});
```

The AST rewriting would transform it to:
```java
Show<int[]> show = PrimitiveShow.intArrayShow();
```

Or for a more complex case with dependencies:
```java
Show<List<Optional<Integer>>> show = witness(new Ty<>() {});
```

Would become:
```java
Show<List<Optional<Integer>>> show = 
    JavaListShow.of(OptionalShow.of(IntegerShow.instance()));
```

## Current Status: Disabled

The AST rewriting is currently **disabled** in the prototype because proper implementation requires:

1. **Phase Management**: AST transformations should occur during the ENTER phase, not ANALYZE. The current implementation runs during ANALYZE when types are already resolved.

2. **Type Attribution**: New AST nodes need to be properly attributed with type information. Simply creating JCTree nodes isn't sufficient - they need associated Symbol and Type information.

3. **Context Preservation**: The transformation needs to maintain source positions, scopes, and other compiler context.

## Enabling AST Rewriting

To enable the actual AST rewriting, you need to:

### Option 1: Uncomment the Transformation Code

In `WitnessResolutionChecker.java`, uncomment the lines in the success branch:

```java
// Uncomment these lines:
// try {
//   result = buildInstantiationTree(plan);
//   if (result != null && tree != null) {
//     result.pos = tree.pos;
//   }
// } catch (Exception e) {
//   trees.printMessage(
//       Diagnostic.Kind.WARNING,
//       "Failed to transform witness call: " + e.getMessage(),
//       tree,
//       currentCompilationUnit);
// }
```

**Note**: This will likely cause compilation errors because the nodes aren't properly attributed.

### Option 2: Proper Implementation (Recommended)

For a production-ready implementation:

1. **Move to ENTER Phase**: Modify the plugin to operate during `TaskEvent.Kind.ENTER` instead of `ANALYZE`:
   ```java
   if (e.getKind() != TaskEvent.Kind.ENTER) {
     return;
   }
   ```

2. **Use Attr for Attribution**: After creating new nodes, use javac's `Attr` class to attribute them:
   ```java
   Attr attr = Attr.instance(context);
   JCTree.JCExpression attributedTree = attr.attribExpr(newTree, env, expectedType);
   ```

3. **Maintain Environment**: Pass and preserve the `Env<AttrContext>` through the transformation.

4. **Handle Edge Cases**: Add proper error handling for cases where:
   - Witness resolution fails
   - Method references can't be built
   - Dependencies have circular references

### Option 3: Alternative Approach - Annotation Processing

Instead of a compiler plugin, consider using annotation processing:

1. Create a custom annotation (e.g., `@ResolveWitness`)
2. Use it to mark methods/fields that need witness resolution
3. Generate witness construction code at compile time
4. This avoids the complexity of AST manipulation

## Build Configuration

The `pom.xml` has been updated to:

1. **Export Internal Packages at Compile Time**:
   ```xml
   <arg>--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED</arg>
   <arg>--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED</arg>
   <arg>--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED</arg>
   <arg>--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED</arg>
   ```

2. **Export at Test Runtime** (surefire configuration):
   ```xml
   <argLine>
     --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
     ...
   </argLine>
   ```

3. **Use source/target instead of release**: Changed from `maven.compiler.release` to `maven.compiler.source` and `maven.compiler.target` to allow `--add-exports`.

## Testing

All existing tests pass with the current implementation:
- Witness resolution validation works correctly
- The infrastructure for AST building is in place and compiles
- The transformation can be enabled by uncommenting a single block

## References

- Baeldung article on compiler plugins: https://www.baeldung.com/java-build-compiler-plugin
- OpenJDK javac documentation
- TreeMaker and JCTree API documentation

## Future Work

- Implement proper phase management (ENTER vs ANALYZE)
- Add type attribution for generated nodes
- Handle all edge cases in witness resolution
- Add integration tests that verify the generated code
- Consider alternative approaches (annotation processing)
- Optimize generated code (avoid redundant witness construction)
