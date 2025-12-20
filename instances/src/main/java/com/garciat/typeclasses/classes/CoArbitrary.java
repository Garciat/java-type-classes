package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.types.Gen;
import com.garciat.typeclasses.utils.Lists;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@TypeClass
public interface CoArbitrary<A> {
  <B> Gen<B> coarbitrary(A a, Gen<B> genB);

  @TypeClass.Witness
  static CoArbitrary<Integer> integerCoArbitrary() {
    return new CoArbitrary<>() {
      @Override
      public <B> Gen<B> coarbitrary(Integer a, Gen<B> genB) {
        return genB.variant(a);
      }
    };
  }

  @TypeClass.Witness
  static <A> CoArbitrary<Optional<A>> optionalCoArbitrary(CoArbitrary<A> coarbA) {
    return new CoArbitrary<>() {
      @Override
      public <B> Gen<B> coarbitrary(Optional<A> optA, Gen<B> genB) {
        if (optA.isPresent()) {
          return coarbA.coarbitrary(optA.get(), genB).variant(1);
        } else {
          return genB.variant(0);
        }
      }
    };
  }

  @TypeClass.Witness
  static <A> CoArbitrary<List<A>> listCoArbitrary(CoArbitrary<A> coarbA) {
    return new CoArbitrary<>() {
      @Override
      public <B> Gen<B> coarbitrary(List<A> listA, Gen<B> genB) {
        Gen<B> resultGen = genB.variant(listA.size());
        for (A a : listA) {
          resultGen = coarbA.coarbitrary(a, resultGen).variant(1);
        }
        return resultGen;
      }
    };
  }

  @TypeClass.Witness
  static <A, B> CoArbitrary<Function<A, B>> functionCoArbitrary(
      Arbitrary<A> arbA, CoArbitrary<B> coarbB) {
    return new CoArbitrary<>() {
      @Override
      public <C> Gen<C> coarbitrary(Function<A, B> f, Gen<C> genC) {
        return Arbitrary.listArbitrary(arbA)
            .arbitrary()
            .flatMap(xs -> CoArbitrary.listCoArbitrary(coarbB).coarbitrary(Lists.map(xs, f), genC));
      }
    };
  }
}
