package com.garciat.typeclasses.examples;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.assertj.core.api.Assertions.assertThat;

import com.garciat.typeclasses.api.Out;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.examples.Example5.Nat.S;
import com.garciat.typeclasses.examples.Example5.Nat.Z;
import com.garciat.typeclasses.impl.utils.Unit;
import org.junit.jupiter.api.Test;

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
  void test1() {
    ReifyNat<S<S<S<Z>>>> reifier = witness(new Ty<>() {});

    assertThat(reifier.reify()).isEqualTo(3);
  }

  @Test
  void test2() {
    NatAdd<S<S<Z>>, S<S<S<Z>>>, S<S<S<S<S<Z>>>>>> adder = witness(new Ty<>() {});

    assertThat(adder).isNotNull();
  }

  @Test
  void test3() {
    ReifyNatAdd<S<S<Z>>, S<S<S<Z>>>> reifyAdd = witness(new Ty<>() {});

    assertThat(reifyAdd.reify()).isEqualTo(5);
  }

  public sealed interface Nat<N extends Nat<N>> {
    record Z() implements Nat<Z> {}

    // Note that we don't store the predecessor!
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

  @TypeClass
  public interface NatAdd<A, B, @Out C> {
    Unit trivial();

    @TypeClass.Witness
    static <B extends Nat<B>> NatAdd<Z, B, B> addZ() {
      return Unit::unit;
    }

    @TypeClass.Witness
    static <A extends Nat<A>, B extends Nat<B>, C extends Nat<C>> NatAdd<S<A>, B, S<C>> addS(
        NatAdd<A, B, C> prev) {
      return Unit::unit;
    }
  }

  @TypeClass
  public interface ReifyNatAdd<A, B> {
    int reify();

    @TypeClass.Witness
    static <A extends Nat<A>, B extends Nat<B>, C extends Nat<C>> ReifyNatAdd<A, B> reifyAddS(
        NatAdd<A, B, C> addAB, ReifyNat<C> reifyC) {
      return reifyC::reify;
    }
  }
}
