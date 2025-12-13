package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@TypeClass
public interface Eq<A> {
  boolean eq(A a1, A a2);

  static <A> boolean eq(Eq<A> eqA, A a1, A a2) {
    return eqA.eq(a1, a2);
  }

  @TypeClass.Witness
  static Eq<Integer> integerEq() {
    return Integer::equals;
  }

  @TypeClass.Witness
  static Eq<String> stringEq() {
    return String::equals;
  }

  @TypeClass.Witness
  static <A> Eq<Optional<A>> optionalEq(Eq<A> eqA) {
    return (optA1, optA2) ->
        optA1.isPresent() && optA2.isPresent()
            ? eqA.eq(optA1.get(), optA2.get())
            : optA1.isEmpty() && optA2.isEmpty();
  }

  @TypeClass.Witness
  static <A> Eq<List<A>> listEq(Eq<A> eqA) {
    return (listA1, listA2) -> {
      if (listA1.size() != listA2.size()) {
        return false;
      }
      for (int i = 0; i < listA1.size(); i++) {
        if (!eqA.eq(listA1.get(i), listA2.get(i))) {
          return false;
        }
      }
      return true;
    };
  }

  @TypeClass.Witness
  static <K, V> Eq<Map<K, V>> mapEq(Eq<K> eqK, Eq<V> eqV) {
    return (map1, map2) -> {
      if (map1.size() != map2.size()) {
        return false;
      }
      for (Map.Entry<K, V> entry1 : map1.entrySet()) {
        boolean found = false;
        for (Map.Entry<K, V> entry2 : map2.entrySet()) {
          if (eqK.eq(entry1.getKey(), entry2.getKey())
              && eqV.eq(entry1.getValue(), entry2.getValue())) {
            found = true;
            break;
          }
        }
        if (!found) {
          return false;
        }
      }
      return true;
    };
  }
}
