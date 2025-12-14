package com.garciat.typeclasses.testclasses;

import com.garciat.typeclasses.api.TypeClass;
import java.util.List;
import java.util.Optional;

@TypeClass
public interface TestShow<A> {
  String show(A a);

  @TypeClass.Witness
  static TestShow<String> stringShow() {
    return s -> "string:" + s;
  }

  @TypeClass.Witness
  static TestShow<Integer> integerShow() {
    return i -> "int:" + i;
  }

  @TypeClass.Witness
  static <A> TestShow<Optional<A>> optionalShow(TestShow<A> showA) {
    return optA -> optA.map(a -> "opt(" + showA.show(a) + ")").orElse("empty");
  }

  @TypeClass.Witness
  static <A> TestShow<List<A>> listShow(TestShow<A> showA) {
    return listA ->
        listA.stream()
            .map(showA::show)
            .reduce((a, b) -> a + "," + b)
            .map(s -> "[" + s + "]")
            .orElse("[]");
  }
}
