package com.garciat.typeclasses.examples;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.examples.Example5.Nat.S;
import com.garciat.typeclasses.examples.Example5.Nat.Z;
import org.junit.jupiter.api.Test;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.assertj.core.api.Assertions.assertThat;

/// Based on:
///
/// ```haskell
/// data Nat = Z | S Nat
///
/// class ReifyNat (a :: Nat) where
///   reifyNat :: Natural
///
/// instance ReifyNat 'Z where
///   reifyNat = 0
///
/// instance ReifyNat a => ReifyNat ('S a) where
///   reifyNat = 1 + reifyNat @a
/// ```
public class Example5 {
  @Test
  void test() {
    ReifyNat<S<S<S<Z>>>> reifier = witness(new Ty<>() {});

    assertThat(reifier.reify()).isEqualTo(3);
  }

  public sealed interface Nat<N extends Nat<N>> {
    record Z() implements Nat<Z> {}

    record S<N extends Nat<N>>() implements Nat<S<N>> {}
  }

  @TypeClass
  public interface ReifyNat<N extends Nat<N>> {
    int reify();

    @TypeClass.Witness
    static ReifyNat<Z> reifyZ() {
      return () -> 0;
    }

    @TypeClass.Witness
    static <N extends Nat<N>> ReifyNat<S<N>> reifyS(ReifyNat<N> rn) {
      return () -> 1 + rn.reify();
    }
  }
}
