package com.garciat.typeclasses;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.assertj.core.api.Assertions.assertThat;

import com.garciat.typeclasses.testclasses.TestShow;
import org.junit.jupiter.api.Test;

/**
 * Test cases for parameterless witness() calls that should be rewritten by the compiler.
 */
final class ParameterlessWitnessTest {

  @Test
  void parameterlessWitnessSimpleType() {
    // Test that parameterless witness() can resolve a simple type
    TestShow<String> show = witness();
    assertThat(show).isNotNull();
    assertThat(show.show("test")).isEqualTo("string:test");
  }

  @Test
  void parameterlessWitnessInteger() {
    TestShow<Integer> show = witness();
    assertThat(show).isNotNull();
    assertThat(show.show(42)).isEqualTo("int:42");
  }
}
