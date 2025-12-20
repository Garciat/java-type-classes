package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.types.Pair;

@TypeClass
public interface RandomGen<G> {
  Pair<Integer, G> next(G gen);

  Pair<G, G> split(G gen);

  @TypeClass.Witness
  static RandomGen<java.util.Random> javaUtilRandomGen() {
    return new RandomGen<>() {
      @Override
      public Pair<Integer, java.util.Random> next(java.util.Random gen) {
        return Pair.of(gen.nextInt(), gen);
      }

      @Override
      public Pair<java.util.Random, java.util.Random> split(java.util.Random gen) {
        java.util.Random gen1 = new java.util.Random(gen.nextLong());
        java.util.Random gen2 = new java.util.Random(gen.nextLong());
        return Pair.of(gen1, gen2);
      }
    };
  }
}
