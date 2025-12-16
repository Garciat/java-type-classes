package com.garciat.typeclasses;

import static com.garciat.typeclasses.TypeClasses.witness;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.classes.*;
import com.garciat.typeclasses.types.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class ExamplesTest {
  @Test
  void example() {
    System.out.println(Show.show(witness(new Ty<>() {}), new int[] {1, 2, 3, 4, 5}));

    System.out.println(Show.show(witness(new Ty<>() {}), new Integer[] {1, 2, 3, 4, 5}));

    Map<String, List<Optional<Integer>>> m1 =
        Map.of(
            "a",
            List.of(Optional.of(1), Optional.empty()),
            "b",
            List.of(Optional.of(2), Optional.of(3)));

    System.out.printf("show(m1) = %s\n", Show.show(witness(new Ty<>() {}), m1));

    List<Sum<Integer>> sums = List.of(new Sum<>(3), new Sum<>(5), new Sum<>(10));

    System.out.printf(
        "combineAll(%s) = %s\n", sums, Monoid.combineAll(witness(new Ty<>() {}), sums));

    System.out.printf("eq(m1, m1) = %s\n", Eq.eq(witness(new Ty<>() {}), m1, m1));

    Optional<Integer> m5 = Optional.of(5);
    Optional<Integer> m10 = Optional.of(10);

    System.out.printf(
        "compare(%s, %s) = %s\n", m5, m10, Ord.compare(witness(new Ty<>() {}), m5, m10));

    Arbitrary<Function<Optional<Integer>, List<Optional<Integer>>>> arbFunc =
        witness(new Ty<>() {});
    var f = arbFunc.arbitrary().generate(42L, 10);

    System.out.println("f(10) = " + f.apply(Optional.of(5)));

    System.out.println(
        Traversable.traverse(
            witness(new Ty<>() {}), witness(new Ty<>() {}), JavaList.of(1, 2, 3), Maybe::just));

    System.out.println(Show.show(witness(new Ty<>() {}), FwdList.of('h', 'e', 'l', 'l', 'o')));

    F3<Integer, Integer, Integer, Integer> sum = SumAllInt.of(witness(new Ty<>() {}));
    System.out.println(sum.apply(1, 2, 3));

    F3<String, JavaList<String>, Integer, Unit> printer = PrintAll.of(witness(new Ty<>() {}));
    printer.apply("Items:", JavaList.of("apple", "banana", "cherry"), 0);

    Foldable<FwdList.Tag> foldableFwdList = witness(new Ty<>() {});

    System.out.println(foldableFwdList.length(FwdList.of(1, 2, 3, 4, 5)));

    System.out.println(foldableFwdList.toList(FwdList.of(1, 2, 3)));
  }
}
