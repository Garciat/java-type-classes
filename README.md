# Type Class resolution for Java

[![codecov](https://codecov.io/gh/Garciat/java-type-classes/branch/main/graph/badge.svg)](https://codecov.io/gh/Garciat/java-type-classes)

## Background

Type classes are a powerful abstraction mechanism popularized by Haskell.

This library implements type class resolution for Java, allowing you to define
type classes and their instances (witnesses) in a modular fashion, and summon
them automatically at runtime.

For a tutorial-like explanation, see: https://garciat.com/posts/java-type-classes/

## Installation

Not yet published to Maven Central.

## API

```java
@interface TypeClass {
  @interface Witness {
  }
}

interface Ty<T> {
} // witness type token

class TypeClasses {
  static <T> T witness(Ty<T> ty);
}
```

Where:

- For a witness type `C<T1, T2, ..., Tn>`, `witness()` looks for witness
  constructors in `C` and `T1, T2, ..., Tn`.
- Witness constructors for a type `T` are its `public static` methods annotated
  with `@TypeClass.Witness`.
- For a witness constructor `C<T> ctor(D1, D2, ..., Dn)`, the witness
  dependencies `D1, D2, ..., Dn` are resolved recursively.
- Resolution fails when multiple witness constructors exist for a witness type,
  after applying overlapping instances reduction.
- Resolution fails when a witness constructor for a witness type cannot be
  found.
- Witness summoning is the result of recursively invoking witness constructors
  up their respective dependency trees.
- `T witness(Ty<T>)` summons a witness of type `T` or fails with a runtime
  exception of type `TypeClasses.WitnessResolutionException`.

## Example

```java
// Type class definition
@TypeClass
public interface Show<T> {
  String show(T value);

  // Helper for inference:
  static <T> String show(Show<T> showT, T value) {
    return showT.show(value);
  }

  // Witness definitions:

  // "Leaf" witness with no dependencies:
  @TypeClass.Witness
  static Show<Integer> integerShow() {
    return i -> Integer.toString(i);
  }

  // Witness with dependencies (constraints):
  @TypeClass.Witness
  static <A> Show<List<A>> listShow(Show<A> showA) {
    return listA -> listA.stream().map(showA::show).collect(Collectors.joining(", ", "[", "]"));
  }
}

// Custom type
record Pair<A, B>(A first, B second) {
  @TypeClass.Witness
  public static <A, B> Show<Pair<A, B>> pairShow(Show<A> showA, Show<B> showB) {
    return pair -> "(" + showA.show(pair.first()) + ", " + showB.show(pair.second()) + ")";
  }
}

// Usage
class Example {
  void main() {
    Pair<Integer, List<Integer>> value = new Pair<>(1, List.of(2, 3, 4));

    // Summon (and use) the Show witness for Pair<Integer, List<Integer>>:
    String s = Show.show(witness(new Ty<>() {
    }), value);

    System.out.println(s); // prints: (1, [2, 3, 4])
  }
}
```

## Other features

- Support for higher-kinded type classes like `Functor<F<_>>`, `Monad<M<_>>`, etc.
    - See the `api.hkt` package for details.
- Support for overlapping instances _a la_ Haskell.
    - Based
      on [this GHC spec](https://ghc.gitlab.haskell.org/ghc/doc/users_guide/exts/instances.html#overlapping-instances).

## Future work

- Annotation processor:
    - To verify witness resolution at compile time.
    - To reify the witness graph at compile time.
    - To support parameterless `witness()` calls.
- Caching of summoned witnesses.
