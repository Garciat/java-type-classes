# Java Type Classes

A type class system for Java, inspired by Haskell's type classes.

## Overview

This library provides a way to define and use type classes in Java, enabling ad-hoc polymorphism through automatic instance resolution. It includes a rich set of predefined type classes and data types with higher-kinded type support.

## Usage

### Basic Example

```java
import static com.garciat.typeclasses.TypeClasses.witness;

// Automatically resolve a Show instance for List<Integer>
Show<List<Integer>> showListInt = witness(new Ty<>() {});
String result = showListInt.show(List.of(1, 2, 3));
// result: "[1, 2, 3]"
```

### Core Type Classes

The library provides several built-in type classes:

- **Show** - Convert values to strings
- **Eq** - Equality testing
- **Ord** - Ordering comparisons
- **Monoid** - Associative binary operations with identity
- **Functor** - Mappable type constructors
- **Applicative** - Application of functions in a context
- **Monad** - Sequential composition of computations
- **Foldable** - Structures that can be folded
- **Traversable** - Structures that can be traversed

### Data Types

The library includes functional data types with type class instances:

- **Maybe** - Optional values (`Just` or `Nothing`)
- **Either** - Sum types (`Left` or `Right`)
- **JavaList** - List with type class instances
- **FwdList** - Functional forward list
- **Parser** - Parser combinators
- **State** - State monad

### Higher-Kinded Types

The library supports higher-kinded types through a defunctionalization encoding:

```java
// Work with Functors abstractly
Functor<Maybe.Tag> functorMaybe = witness(new Ty<>() {});
TApp<Maybe.Tag, Integer> maybeInt = Maybe.just(42);
TApp<Maybe.Tag, String> maybeStr = functorMaybe.map(Object::toString, maybeInt);
```

## Examples

See the `Examples` class for comprehensive usage examples:

```bash
mvn compile exec:java -Dexec.mainClass="com.garciat.typeclasses.Examples"
```

## API Structure

### Public API

The main entry point is `TypeClasses.witness()` which resolves type class instances. All type classes (marked with `@TypeClass`) and data types are part of the public API.

Key public components:
- `TypeClasses.witness()` - Resolve type class instances
- `Ty<T>` - Type token for capturing types
- `Ctx<T>` - Context token for explicit instances
- `@TypeClass` - Annotation for defining type classes
- `Kind`, `TApp`, `TPar`, `TagBase` - Higher-kinded type infrastructure

### Internal Implementation

Internal implementation details (parsing, unification, witness resolution algorithms) are package-private and should not be relied upon by library users.

## Building

```bash
mvn clean compile test
```

## Conventions

- Use google-java-format for code formatting
- Java 21 is required

## License

See repository for license information.
