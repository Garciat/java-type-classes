package com.garciat.typeclasses;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.junit.jupiter.api.Assertions.*;

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
    assertNotNull(showString);
    assertEquals("string:test", showString.show("test"));
  }

  @Test
  void witnessWithDependency() {
    TestShow<Optional<String>> showOptional = witness(new Ty<>() {});
    assertNotNull(showOptional);
    assertEquals("opt(string:test)", showOptional.show(Optional.of("test")));
    assertEquals("empty", showOptional.show(Optional.empty()));
  }

  @Test
  void witnessWithMultipleDependencies() {
    TestEq<List<String>> eqList = witness(new Ty<>() {});
    assertNotNull(eqList);
    assertTrue(eqList.eq(List.of("a", "b"), List.of("a", "b")));
    assertFalse(eqList.eq(List.of("a", "b"), List.of("a", "c")));
  }

  // ============================================
  // Witness constructor lookup tests
  // ============================================

  @Test
  void witnessLookupsInTypeClass() {
    // Lookup should find witnesses in the type class interface (TestShow)
    TestShow<String> show = witness(new Ty<>() {});
    assertNotNull(show);
  }

  @Test
  void witnessLookupsInTypeArguments() {
    // Lookup should find witnesses in type arguments (String has witness in TestShow)
    TestShow<Optional<String>> show = witness(new Ty<>() {});
    assertNotNull(show);
  }

  // ============================================
  // Public static @TypeClass.Witness annotation tests
  // ============================================

  @Test
  void witnessRequiresPublicStaticMethod() {
    // This should work - witness is public static
    TestShow<String> show = witness(new Ty<>() {});
    assertNotNull(show);
  }

  @Test
  void witnessNotFoundForUnannotatedTypes() {
    // NoWitnessType has no @TypeClass.Witness methods
    assertThrows(
        TypeClasses.WitnessResolutionException.class,
        () -> witness(new Ty<TestShow<NoWitnessType>>() {}));
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
    assertNotNull(show);
    assertEquals(
        "[opt(string:a),empty,opt(string:b)]",
        show.show(List.of(Optional.of("a"), Optional.empty(), Optional.of("b"))));
  }

  @Test
  void witnessDeepRecursion() {
    // List<List<List<String>>> should resolve recursively
    TestShow<List<List<List<String>>>> show = witness(new Ty<>() {});
    assertNotNull(show);
  }

  // ============================================
  // Overlapping instances tests
  // ============================================

  @Test
  void overlappingInstancesMoreSpecificWins() {
    // When we have both general and specific instances,
    // the more specific (overlapping) one should win
    com.garciat.typeclasses.testclasses.OverlapShow<Integer> show = witness(new Ty<>() {});
    assertNotNull(show);
    // Should use the specific Integer instance
    assertEquals("Integer: 42", show.show(42));
  }

  @Test
  void overlappableInstanceCanBeOverridden() {
    // OVERLAPPABLE instances can be overridden by more specific ones
    com.garciat.typeclasses.testclasses.OverlapShow<String> show = witness(new Ty<>() {});
    assertNotNull(show);
    assertEquals("String: test", show.show("test"));
  }

  // ============================================
  // Ambiguity detection tests
  // ============================================

  @Test
  void ambiguousWitnessesThrow() {
    // AmbiguousShow has two witness constructors without overlap markers
    assertThrows(
        TypeClasses.WitnessResolutionException.class,
        () -> witness(new Ty<com.garciat.typeclasses.testclasses.AmbiguousShow<String>>() {}));
  }

  // ============================================
  // Not found error tests
  // ============================================

  @Test
  void witnessNotFoundThrows() {
    assertThrows(
        TypeClasses.WitnessResolutionException.class,
        () -> witness(new Ty<TestShow<NoWitnessType>>() {}));
  }

  @Test
  void witnessNotFoundNestedThrows() {
    // List<NoWitnessType> - the dependency TestShow<NoWitnessType> cannot be found
    assertThrows(
        TypeClasses.WitnessResolutionException.class,
        () -> witness(new Ty<TestShow<List<NoWitnessType>>>() {}));
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

    assertNotNull(listShow);
    assertEquals(
        "[custom:a,custom:b]", listShow.show(List.of(new CustomType("a"), new CustomType("b"))));
  }

  @Test
  void witnessSummoningBuildsTree() {
    // Verify that the witness is actually constructed correctly
    // by checking its behavior with nested types
    TestShow<Optional<List<String>>> show = witness(new Ty<>() {});

    assertEquals(
        "opt([string:a,string:b,string:c])", show.show(Optional.of(List.of("a", "b", "c"))));
    assertEquals("empty", show.show(Optional.empty()));
  }

  // ============================================
  // WitnessResolutionException tests
  // ============================================

  @Test
  void witnessResolutionExceptionHasMessage() {
    TypeClasses.WitnessResolutionException ex =
        assertThrows(
            TypeClasses.WitnessResolutionException.class,
            () -> witness(new Ty<TestShow<NoWitnessType>>() {}));

    assertNotNull(ex.getMessage());
    assertTrue(
        ex.getMessage().contains("NoWitnessType"),
        "Error message should mention the type that failed: " + ex.getMessage());
  }

  @Test
  void witnessResolutionExceptionForAmbiguous() {
    TypeClasses.WitnessResolutionException ex =
        assertThrows(
            TypeClasses.WitnessResolutionException.class,
            () -> witness(new Ty<com.garciat.typeclasses.testclasses.AmbiguousShow<String>>() {}));

    assertNotNull(ex.getMessage());
    assertTrue(
        ex.getMessage().toLowerCase().contains("ambiguous"),
        "Error message should mention ambiguity: " + ex.getMessage());
  }

  @Test
  void witnessMapWithDependencies() {
    // Map<String, Integer> requires TestEq<String> and TestEq<Integer>
    TestEq<Map<String, Integer>> eqMap = witness(new Ty<>() {});
    assertNotNull(eqMap);

    Map<String, Integer> map1 = Map.of("a", 1, "b", 2);
    Map<String, Integer> map2 = Map.of("a", 1, "b", 2);
    Map<String, Integer> map3 = Map.of("a", 1, "b", 3);

    assertTrue(eqMap.eq(map1, map2));
    assertFalse(eqMap.eq(map1, map3));
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
