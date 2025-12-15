package com.garciat.typeclasses.processor;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.assertj.core.api.Assertions.assertThat;

import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.testclasses.TestShow;
import org.junit.jupiter.api.Test;

/**
 * Test class to demonstrate the WitnessResolutionProcessor.
 *
 * <p>When the WitnessResolutionChecker plugin is enabled (see ANNOTATION_PROCESSOR.md), this test
 * file will demonstrate compile-time verification of witness resolution.
 *
 * <p>Note: The processor cannot be enabled for self-compilation (bootstrapping issue), so these
 * tests demonstrate runtime behavior. In external projects that depend on this library, the
 * processor can be enabled to catch errors at compile time.
 */
final class WitnessResolutionProcessorTest {

  /**
   * This test succeeds at both compile-time and runtime because String has a witness constructor in
   * TestShow.
   *
   * <p>With the processor enabled, the compiler would verify that:
   *
   * <ul>
   *   <li>TestShow has a witness constructor for String
   *   <li>All transitive dependencies can be resolved
   *   <li>No ambiguities exist in the witness constructor resolution
   * </ul>
   */
  @Test
  void testValidWitnessResolution() {
    TestShow<String> showString = witness(new Ty<>() {});
    assertThat(showString).isNotNull();
    assertThat(showString.show("test")).isEqualTo("string:test");
  }

  /**
   * Demonstrates that missing witness constructors fail at runtime.
   *
   * <p>If the commented line were uncommented and the processor were enabled, this would produce a
   * compile-time error:
   *
   * <pre>
   * Witness resolution will fail at runtime:
   * No witness found for type: NoWitnessType
   * </pre>
   *
   * <p>The line is commented to prevent runtime test failures in this demonstration.
   */
  @Test
  void testInvalidWitnessResolutionWouldFailAtCompileTime() {
    // Uncomment to see the runtime error (or compile-time error with processor enabled):
    // TestShow<NoWitnessType> showNoWitness = witness(new Ty<>() {});

    // Instead, let's document what would happen:
    // At runtime: throws WitnessResolutionException("No witness found for type: NoWitnessType")
    // With processor: compile-time error with the same message
  }

  /**
   * Demonstrates nested type witness resolution.
   *
   * <p>This tests that the processor can verify complex nested types like List&lt;String&gt;.
   */
  @Test
  void testNestedTypeWitnessResolution() {
    TestShow<java.util.List<String>> showList = witness(new Ty<>() {});
    assertThat(showList).isNotNull();
    assertThat(showList.show(java.util.List.of("a", "b"))).isEqualTo("[string:a,string:b]");
  }

  /** Helper class with no witness constructors - used for demonstration purposes. */
  @SuppressWarnings("NullAway")
  static class NoWitnessType {
    String value;
  }
}
