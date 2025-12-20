package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@TypeClass
public interface Show<A> {
  String show(A a);

  static <A> String show(Show<A> showA, A a) {
    return showA.show(a);
  }

  @TypeClass.Witness
  static Show<Integer> integerShow() {
    return i -> Integer.toString(i);
  }

  @TypeClass.Witness
  static Show<String> stringShow() {
    return s -> "\"" + s + "\"";
  }

  @TypeClass.Witness
  static <A> Show<Optional<A>> optionalShow(Show<A> showA) {
    return optA -> optA.map(a -> "Some(" + showA.show(a) + ")").orElse("None");
  }

  @TypeClass.Witness
  static <A> Show<A[]> arrayShow(Show<A> showA) {
    return arrayA ->
        Arrays.stream(arrayA).map(showA::show).collect(Collectors.joining(", ", "[", "]"));
  }

  @TypeClass.Witness
  static Show<int[]> intArrayShow() {
    return arrayA ->
        Arrays.stream(arrayA)
            .mapToObj(Integer::toString)
            .collect(Collectors.joining(", ", "[", "]"));
  }

  @TypeClass.Witness
  static <A> Show<List<A>> listShow(Show<A> showA) {
    return listA -> listA.stream().map(showA::show).collect(Collectors.joining(", ", "[", "]"));
  }

  @TypeClass.Witness
  static <K, V> Show<Map<K, V>> mapShow(Show<K> showK, Show<V> showV) {
    return mapKV ->
        mapKV.entrySet().stream()
            .map(entry -> showK.show(entry.getKey()) + ": " + showV.show(entry.getValue()))
            .collect(Collectors.joining(", ", "{", "}"));
  }
}
