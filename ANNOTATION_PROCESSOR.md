# Witness Resolution Annotation Processor

This annotation processor verifies at compile time that calls to `TypeClasses.witness(Ty<T>)` will succeed in terms of witness constructor resolution.

## How It Works

The processor:
1. Scans compiled code for calls to `TypeClasses.witness()`
2. Extracts the type argument `T` from `Ty<T>`
3. Runs the witness resolution algorithm at compile time
4. Reports compilation errors for witness resolution failures (not found, ambiguous, etc.)

## Usage

To enable the witness resolution checker in your project, add the following to your `pom.xml`:

```xml
<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <compilerArgs>
          <arg>-Xplugin:WitnessResolutionChecker</arg>
          <!-- Required for Java 21+ compiler plugin access -->
          <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED</arg>
          <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED</arg>
          <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED</arg>
        </compilerArgs>
      </configuration>
      <dependencies>
        <dependency>
          <groupId>com.garciat.typeclasses</groupId>
          <artifactId>java-type-classes</artifactId>
          <version>1.0-SNAPSHOT</version>
        </dependency>
      </dependencies>
    </plugin>
  </plugins>
</build>
```

## Examples

### Valid Witness Resolution

This will compile successfully because `String` has a witness constructor:

```java
TestShow<String> showString = witness(new Ty<>() {});
```

### Invalid Witness Resolution

This will produce a compile-time error:

```java
// Error: No witness found for type: NoWitnessType
TestShow<NoWitnessType> showNoWitness = witness(new Ty<>() {});
```

### Ambiguous Witness Resolution

If multiple witness constructors match without proper overlap annotations:

```java
// Error: Ambiguous witnesses found for type: SomeType
SomeTypeClass<SomeType> instance = witness(new Ty<>() {});
```

## Limitations

- The processor uses reflection-based witness resolution, so it can only verify types that are available on the classpath at compile time
- Complex generic types may not be fully verified if type parameters cannot be resolved
- The processor is designed to catch common errors but may not detect all edge cases

## Implementation Details

The processor is implemented as a JavaC compiler plugin using the `com.sun.source.util.Plugin` API. It:

- Uses `TreePathScanner` to find method invocations
- Checks if the invocation is to `TypeClasses.witness()`
- Extracts type information from the AST
- Runs the same `WitnessResolution.resolve()` logic used at runtime
- Reports errors using the standard Java diagnostics API

## Benefits

- **Early Error Detection**: Catch witness resolution failures at compile time instead of runtime
- **Better IDE Support**: IDEs can show compilation errors inline as you type
- **Type Safety**: Ensures that witness calls will succeed before running tests
- **Documentation**: Compilation errors clearly explain why witness resolution fails
