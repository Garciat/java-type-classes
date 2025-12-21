package com.garciat.typeclasses.processor.examples;

import static com.garciat.typeclasses.TypeClasses.witness;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.processor.examples.TList.TCons;
import com.garciat.typeclasses.processor.examples.TList.TNil;

interface TList<T extends TList.Base<T>> {
  sealed interface Base<A extends Base<A>> {}

  final class TCons<T, TS extends Base<TS>> implements Base<TCons<T, TS>> {}

  final class TNil implements Base<TNil> {}
}

@TypeClass
interface In<TS extends TList.Base<TS>, Y> {
  @TypeClass.Witness
  static <X, XS extends TList.Base<XS>> In<TCons<X, XS>, X> here() {
    return new In<>() {};
  }

  @TypeClass.Witness(overlap = TypeClass.Witness.Overlap.OVERLAPPABLE)
  static <X, XS extends TList.Base<XS>, Y> In<TCons<X, XS>, Y> there(In<XS, Y> there) {
    return new In<>() {};
  }
}

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
  void main() {
    In<TCons<Byte, TCons<Short, TCons<Integer, TNil>>>, Short> _ = witness(new Ty<>() {});
  }
}
