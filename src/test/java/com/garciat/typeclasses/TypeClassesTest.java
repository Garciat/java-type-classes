package com.garciat.typeclasses;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garciat.typeclasses.api.Ctx;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.testclasses.TestEq;
import com.garciat.typeclasses.testclasses.TestShow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class TypeClassesTest {

  // ============================================
  // Basic witness resolution tests
  // ============================================

  @Test
  void witnessSimpleTypeClass() {
    TestShow<String> showString = witness(new Ty<>() {});
    assertThat(showString).isNotNull();
    assertThat(showString.show("test")).isEqualTo("string:test");
  }

  @Test
  void witnessWithDependency() {
    TestShow<Optional<String>> showOptional = witness(new Ty<>() {});
    assertThat(showOptional).isNotNull();
    assertThat(showOptional.show(Optional.of("test"))).isEqualTo("opt(string:test)");
    assertThat(showOptional.show(Optional.empty())).isEqualTo("empty");
  }

  @Test
  void witnessWithMultipleDependencies() {
    TestEq<List<String>> eqList = witness(new Ty<>() {});
    assertThat(eqList).isNotNull();
    assertThat(eqList.eq(List.of("a", "b"), List.of("a", "b"))).isTrue();
    assertThat(eqList.eq(List.of("a", "b"), List.of("a", "c"))).isFalse();
  }

  // ============================================
  // Witness constructor lookup tests
  // ============================================

  @Test
  void witnessLookupsInTypeClass() {
    // Lookup should find witnesses in the type class interface (TestShow)
    TestShow<String> show = witness(new Ty<>() {});
    assertThat(show).isNotNull();
  }

  @Test
  void witnessLookupsInTypeArguments() {
    // Lookup should find witnesses in type arguments (String has witness in TestShow)
    TestShow<Optional<String>> show = witness(new Ty<>() {});
    assertThat(show).isNotNull();
  }

  // ============================================
  // Public static @TypeClass.Witness annotation tests
  // ============================================

  @Test
  void witnessRequiresPublicStaticMethod() {
    // This should work - witness is public static
    TestShow<String> show = witness(new Ty<>() {});
    assertThat(show).isNotNull();
  }

  @Test
  void witnessNotFoundForUnannotatedTypes() {
    // NoWitnessType has no @TypeClass.Witness methods
    assertThatThrownBy(() -> witness(new Ty<TestShow<NoWitnessType>>() {}))
        .isInstanceOf(TypeClasses.WitnessResolutionException.class);
  }

  // ============================================
  // Recursive dependency resolution tests
  // ============================================

  @Test
  void witnessRecursiveDependencies() {
    // List<Optional<String>> requires:
    // 1. listShow(TestShow<Optional<String>>)
    // 2. optionalShow(TestShow<String>)
    // 3. stringShow()
    TestShow<List<Optional<String>>> show = witness(new Ty<>() {});
    assertThat(show).isNotNull();
    assertThat(show.show(List.of(Optional.of("a"), Optional.empty(), Optional.of("b"))))
        .isEqualTo("[opt(string:a),empty,opt(string:b)]");
  }

  @Test
  void witnessDeepRecursion() {
    // List<List<List<String>>> should resolve recursively
    TestShow<List<List<List<String>>>> show = witness(new Ty<>() {});
    assertThat(show).isNotNull();
  }

  // ============================================
  // Overlapping instances tests
  // ============================================

  @Test
  void overlappingInstancesMoreSpecificWins() {
    // When we have both general and specific instances,
    // the more specific (overlapping) one should win
    com.garciat.typeclasses.testclasses.OverlapShow<Integer> show = witness(new Ty<>() {});
    assertThat(show).isNotNull();
    // Should use the specific Integer instance
    assertThat(show.show(42)).isEqualTo("Integer: 42");
  }

  @Test
  void overlappableInstanceCanBeOverridden() {
    // OVERLAPPABLE instances can be overridden by more specific ones
    com.garciat.typeclasses.testclasses.OverlapShow<String> show = witness(new Ty<>() {});
    assertThat(show).isNotNull();
    assertThat(show.show("test")).isEqualTo("String: test");
  }

  // ============================================
  // Ambiguity detection tests
  // ============================================

  @Test
  void ambiguousWitnessesThrow() {
    // AmbiguousShow has two witness constructors without overlap markers
    assertThatThrownBy(
            () -> witness(new Ty<com.garciat.typeclasses.testclasses.AmbiguousShow<String>>() {}))
        .isInstanceOf(TypeClasses.WitnessResolutionException.class);
  }

  // ============================================
  // Not found error tests
  // ============================================

  @Test
  void witnessNotFoundThrows() {
    assertThatThrownBy(() -> witness(new Ty<TestShow<NoWitnessType>>() {}))
        .isInstanceOf(TypeClasses.WitnessResolutionException.class);
  }

  @Test
  void witnessNotFoundNestedThrows() {
    // List<NoWitnessType> - the dependency TestShow<NoWitnessType> cannot be found
    assertThatThrownBy(() -> witness(new Ty<TestShow<List<NoWitnessType>>>() {}))
        .isInstanceOf(TypeClasses.WitnessResolutionException.class);
  }

  // ============================================
  // Context/witness summoning tests
  // ============================================

  @Test
  void witnessSummoningWithContext() {
    // Provide a custom witness via context
    CustomType customValue = new CustomType("test");
    TestShow<CustomType> customShow = c -> "custom:" + c.value;

    TestShow<List<CustomType>> listShow = witness(new Ty<>() {}, new Ctx<>(customShow) {});

    assertThat(listShow).isNotNull();
    assertThat(listShow.show(List.of(new CustomType("a"), new CustomType("b"))))
        .isEqualTo("[custom:a,custom:b]");
  }

  @Test
  void witnessSummoningBuildsTree() {
    // Verify that the witness is actually constructed correctly
    // by checking its behavior with nested types
    TestShow<Optional<List<String>>> show = witness(new Ty<>() {});

    assertThat(show.show(Optional.of(List.of("a", "b", "c"))))
        .isEqualTo("opt([string:a,string:b,string:c])");
    assertThat(show.show(Optional.empty())).isEqualTo("empty");
  }

  // ============================================
  // WitnessResolutionException tests
  // ============================================

  @Test
  void witnessResolutionExceptionHasMessage() {
    assertThatThrownBy(() -> witness(new Ty<TestShow<NoWitnessType>>() {}))
        .isInstanceOf(TypeClasses.WitnessResolutionException.class)
        .hasMessageContaining("NoWitnessType");
  }

  @Test
  void witnessResolutionExceptionForAmbiguous() {
    assertThatThrownBy(
            () -> witness(new Ty<com.garciat.typeclasses.testclasses.AmbiguousShow<String>>() {}))
        .isInstanceOf(TypeClasses.WitnessResolutionException.class)
        .hasMessageContaining("mbiguous");
  }

  @Test
  void witnessMapWithDependencies() {
    // Map<String, Integer> requires TestEq<String> and TestEq<Integer>
    TestEq<Map<String, Integer>> eqMap = witness(new Ty<>() {});
    assertThat(eqMap).isNotNull();

    Map<String, Integer> map1 = Map.of("a", 1, "b", 2);
    Map<String, Integer> map2 = Map.of("a", 1, "b", 2);
    Map<String, Integer> map3 = Map.of("a", 1, "b", 3);

    assertThat(eqMap.eq(map1, map2)).isTrue();
    assertThat(eqMap.eq(map1, map3)).isFalse();
  }

  // ============================================
  // Test helper classes
  // ============================================

  static class NoWitnessType {
    String value;
  }

  static class CustomType {
    String value;

    CustomType(String value) {
      this.value = value;
    }
  }
}
