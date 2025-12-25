package com.garciat.typeclasses.examples;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.assertj.core.api.Assertions.assertThat;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.examples.Example2.TList.TCons;
import com.garciat.typeclasses.examples.Example2.TList.TNil;
import org.junit.jupiter.api.Test;

/// Based on:
///
/// ```haskell
/// class In (xs :: [k]) (x :: k)
///
/// instance In (x ': xs) x
///
/// instance {-# OVERLAPPABLE #-} In xs y => In (x ': xs) y
///
/// example :: In '[Int, Bool, Char] Bool => ()
/// example = ()
/// ```
public class Example2 {
  @Test
  void main() {
    In<TCons<Byte, TCons<Short, TCons<Integer, TNil>>>, Short> w = witness(new Ty<>() {});

    assertThat(w).isNotNull();
  }

  public interface TList<T extends TList<T>> {
    record TNil() implements TList<TNil> {}

    record TCons<T, TS extends TList<TS>>() implements TList<TCons<T, TS>> {}
  }

  @TypeClass
  public interface In<TS extends TList<TS>, Y> {
    @TypeClass.Witness
    static <X, XS extends TList<XS>> In<TCons<X, XS>, X> here() {
      return new In<>() {};
    }

    @TypeClass.Witness(overlap = TypeClass.Witness.Overlap.OVERLAPPABLE)
    static <X, XS extends TList<XS>, Y> In<TCons<X, XS>, Y> there(In<XS, Y> there) {
      return new In<>() {};
    }
  }
}
