package com.garciat.typeclasses.testclasses;

import com.garciat.typeclasses.api.TypeClass;
import java.util.List;
import java.util.Map;

@TypeClass
public interface TestEq<A> {
  boolean eq(A a1, A a2);

  @TypeClass.Witness
  static TestEq<String> stringEq() {
    return String::equals;
  }

  @TypeClass.Witness
  static TestEq<Integer> integerEq() {
    return Integer::equals;
  }

  @TypeClass.Witness
  static <A> TestEq<List<A>> listEq(TestEq<A> eqA) {
    return (l1, l2) -> {
      if (l1.size() != l2.size()) return false;
      for (int i = 0; i < l1.size(); i++) {
        if (!eqA.eq(l1.get(i), l2.get(i))) return false;
      }
      return true;
    };
  }

  @TypeClass.Witness
  static <K, V> TestEq<Map<K, V>> mapEq(TestEq<K> eqK, TestEq<V> eqV) {
    return (map1, map2) -> {
      if (map1.size() != map2.size()) return false;
      for (Map.Entry<K, V> entry1 : map1.entrySet()) {
        boolean found = false;
        for (Map.Entry<K, V> entry2 : map2.entrySet()) {
          if (eqK.eq(entry1.getKey(), entry2.getKey())
              && eqV.eq(entry1.getValue(), entry2.getValue())) {
            found = true;
            break;
          }
        }
        if (!found) return false;
      }
      return true;
    };
  }
}
