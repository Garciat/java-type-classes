package com.garciat.typeclasses.processor.examples;

import static com.garciat.typeclasses.TypeClasses.witness;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.api.TypeClass;
import java.util.List;
import java.util.stream.Collectors;

// Type class definition
@TypeClass
interface Show<T> {
  String show(T value);

  // Helper for inference:
  static <T> String show(Show<T> showT, T value) {
    return showT.show(value);
  }

  // Witness definitions:

  // "Leaf" witness with no dependencies:
  @TypeClass.Witness
  static Show<Integer> integerShow() {
    return i -> Integer.toString(i);
  }

  // Witness with dependencies (constraints):
  @TypeClass.Witness
  static <A> Show<List<A>> listShow(Show<A> showA) {
    return listA -> listA.stream().map(showA::show).collect(Collectors.joining(", ", "[", "]"));
  }
}

// Custom type
record Pair<A, B>(A first, B second) {
  @TypeClass.Witness
  public static <A, B> Show<Pair<A, B>> pairShow(Show<A> showA, Show<B> showB) {
    return pair -> "(" + showA.show(pair.first()) + ", " + showB.show(pair.second()) + ")";
  }
}

// Usage
public class Example1 {
  void main() {
    Pair<Integer, List<Integer>> value = new Pair<>(1, List.of(2, 3, 4));

    // Summon (and use) the Show witness for Pair<Integer, List<Integer>>:
    String s = Show.show(witness(new Ty<>() {}), value);

    System.out.println(s); // prints: (1, [2, 3, 4])
  }
}
