package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.TypeClass.Witness;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.types.F1;
import com.garciat.typeclasses.types.F2;
import com.garciat.typeclasses.types.F3;
import java.util.List;
import java.util.function.Function;

@TypeClass
public interface SumAllInt<A> {
  A sum(List<Integer> list);

  static <T> T of(SumAllInt<T> sumAllInt) {
    return sumAllInt.sum(List.of());
  }

  @Witness
  static SumAllInt<Integer> base() {
    return list -> list.stream().mapToInt(Integer::intValue).sum();
  }

  @Witness
  static <A, R> SumAllInt<Function<A, R>> func(SumAllInt<R> sumR, TyEq<A, Integer> eq) {
    return list -> a -> sumR.sum(Lists.concat(list, List.of(eq.castL(a))));
  }

  @Witness
  static <A, R> SumAllInt<F1<A, R>> func1(SumAllInt<Function<A, R>> sumR) {
    return list -> F1.of(sumR.sum(list));
  }

  @Witness
  static <A, B, R> SumAllInt<F2<A, B, R>> func2(SumAllInt<Function<A, Function<B, R>>> sumR) {
    return list -> F2.of(sumR.sum(list));
  }

  @Witness
  static <A, B, C, R> SumAllInt<F3<A, B, C, R>> func3(
      SumAllInt<Function<A, Function<B, Function<C, R>>>> sumR) {
    return list -> F3.of(sumR.sum(list));
  }
}
