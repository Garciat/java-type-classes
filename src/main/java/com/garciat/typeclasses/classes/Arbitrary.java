package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.types.Gen;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@TypeClass
public interface Arbitrary<A> {
  Gen<A> arbitrary();

  @TypeClass.Witness
  static Arbitrary<Integer> integerArbitrary() {
    return () -> Gen.chooseInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  @TypeClass.Witness
  static <A> Arbitrary<Optional<A>> optionalArbitrary(Arbitrary<A> arbA) {
    return () -> {
      Gen<A> genA = arbA.arbitrary();
      return (seed, size) -> {
        Gen<Integer> genBool = Gen.chooseInt(0, 2);
        if (genBool.generate(seed, size) == 0) {
          return Optional.of(genA.generate(seed + 1, size));
        } else {
          return Optional.empty();
        }
      };
    };
  }

  @TypeClass.Witness
  static <A> Arbitrary<List<A>> listArbitrary(Arbitrary<A> arbA) {
    return () -> arbA.arbitrary().listOf();
  }

  @TypeClass.Witness
  static <A, B> Arbitrary<Function<A, B>> functionArbitrary(
      CoArbitrary<A> coarb, Arbitrary<B> arbB) {
    return () -> {
      Gen<B> genB = arbB.arbitrary();
      return (seed, size) -> a -> coarb.coarbitrary(a, genB).generate(seed, size);
    };
  }
}
