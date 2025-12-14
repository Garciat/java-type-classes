package com.garciat.typeclasses.classes;

import static com.garciat.typeclasses.types.Unit.unit;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.TypeClass.Witness;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.types.F1;
import com.garciat.typeclasses.types.F2;
import com.garciat.typeclasses.types.F3;
import com.garciat.typeclasses.types.Unit;
import java.util.List;
import java.util.function.Function;

/**
 * @implNote <a href="https://wiki.haskell.org/Varargs">Source</a>
 */
@TypeClass
public interface PrintAll<T> {
  T printAll(List<String> strings);

  static <T> T of(PrintAll<T> printAll) {
    return printAll.printAll(List.of());
  }

  @Witness
  static PrintAll<Unit> base() {
    return strings -> {
      for (String s : strings) {
        System.out.println(s);
      }
      return unit();
    };
  }

  @Witness
  static <A, R> PrintAll<Function<A, R>> func(Show<A> showA, PrintAll<R> printR) {
    return strings -> a -> printR.printAll(Lists.concat(strings, List.of(showA.show(a))));
  }

  @Witness
  static <A, R> PrintAll<F1<A, R>> func1(PrintAll<Function<A, R>> printR) {
    return strings -> F1.of(printR.printAll(strings));
  }

  @Witness
  static <A, B, R> PrintAll<F2<A, B, R>> func2(PrintAll<Function<A, Function<B, R>>> printR) {
    return strings -> F2.of(printR.printAll(strings));
  }

  @Witness
  static <A, B, C, R> PrintAll<F3<A, B, C, R>> func3(
      PrintAll<Function<A, Function<B, Function<C, R>>>> printR) {
    return strings -> F3.of(printR.printAll(strings));
  }
}
