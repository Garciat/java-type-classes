package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.types.Pair;

@TypeClass
public interface Random<A> {
  <G> Pair<A, G> random(RandomGen<G> randomGen, G gen);

  @TypeClass.Witness
  static Random<Integer> integerRandom() {
    return new Random<>() {
      @Override
      public <G> Pair<Integer, G> random(RandomGen<G> randomGen, G gen) {
        return randomGen.next(gen);
      }
    };
  }
}
