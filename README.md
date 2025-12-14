# Type Class resolution for Java

[![codecov](https://codecov.io/gh/Garciat/java-type-classes/branch/main/graph/badge.svg)](https://codecov.io/gh/Garciat/java-type-classes)

See: https://garciat.com/posts/java-type-classes/

The core API of this library is:

```java
@interface TypeClass {
  @interface Witness {}
}

public class TypeClasses {
  public static <T> T witness(Ty<T> ty);
}
```

Where:

- For a witness type `C<T1, T2, ..., Tn>`, `witness()` looks for witness
  constructors in `C` and `T1, T2, ..., Tn`.
- Witness constructors for a type `T` are its `public static` methods annotated
  with `@TypeClass.Witness`.
- For a witness constructor `C<T> ctor(D1, D2, ..., Dn)`, the witness
  dependencies `D1, D2, ..., Dn` are resolved recursively.
- Overlapping instances behavior per
  [this spec](https://ghc.gitlab.haskell.org/ghc/doc/users_guide/exts/instances.html#overlapping-instances),
  except for the incoherent instances part.
- Resolution fails when multiple witness constructors exist for a witness type,
  after applying overlapping instances reduction.
- Resolution fails when a witness constructor for a witness type cannot be
  found.
- Witness summoning is the result of recursively invoking witness constructors
  up their respective dependency trees.
- `T witness(Ty<T>)` summons a witness of type `T` or fails with a runtime
  exception of type `TypeClasses.WitnessResolutionException`.

Now, there are multiple built-in type classes and types in the `classes` and
`types` packages, respectively. Their usage is **completely optional**. If your
code does not refer to any of the types defined there, then witness resolution
will not take them into account. As mentioned above, the resolution of a witness
`C<T>` is completely local to the definitions of `C` and `T`.

For examples, check out the
[ExamplesTest.java](https://github.com/Garciat/java-type-classes/blob/main/src/test/java/com/garciat/typeclasses/ExamplesTest.java)
file and its respective imports.
