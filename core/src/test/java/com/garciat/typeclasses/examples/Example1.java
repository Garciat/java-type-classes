package com.garciat.typeclasses.examples;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.assertj.core.api.Assertions.assertThat;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.api.TypeClass;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class Example1 {
  @Test
  void main() {
    Pair<Integer, List<Integer>> value = new Pair<>(1, List.of(2, 3, 4));

    String s = Show.show(witness(new Ty<>() {}), value);

    assertThat(s).isEqualTo("(1, [2, 3, 4])");
  }

  @TypeClass
  public interface Show<T> {
    String show(T value);

    static <T> String show(Show<T> showT, T value) {
      return showT.show(value);
    }

    @TypeClass.Witness
    static Show<Integer> integerShow() {
      return i -> Integer.toString(i);
    }

    @TypeClass.Witness
    static <A> Show<List<A>> listShow(Show<A> showA) {
      return listA -> listA.stream().map(showA::show).collect(Collectors.joining(", ", "[", "]"));
    }
  }

  public record Pair<A, B>(A first, B second) {
    @TypeClass.Witness
    public static <A, B> Show<Pair<A, B>> pairShow(Show<A> showA, Show<B> showB) {
      return pair -> "(" + showA.show(pair.first()) + ", " + showB.show(pair.second()) + ")";
    }
  }
}
