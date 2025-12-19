package com.garciat.typeclasses;

import static com.garciat.typeclasses.TypeClasses.witness;

import com.garciat.typeclasses.classes.Show;
import org.junit.jupiter.api.Test;

/**
 * Test the parameterless witness() method with the compiler plugin.
 * 
 * This test verifies that the compiler plugin correctly rewrites calls to the parameterless
 * witness() method into direct witness constructor invocations.
 */
final class ParameterlessWitnessTest {

  @Test
  void parameterlessWitnessForSimpleType() {
    // This should be rewritten by the plugin to: Show.integerShow()
    Show<Integer> showInteger = witness();
    
    // If the plugin worked, this should execute without throwing UnsupportedOperationException
    String result = showInteger.show(42);
    System.out.println("Show<Integer>: " + result);
  }

  @Test
  void parameterlessWitnessForComplexType() {
    // This should be rewritten by the plugin to: Show.listShow(Show.integerShow())
    Show<java.util.List<Integer>> showList = witness();
    
    String result = showList.show(java.util.List.of(1, 2, 3));
    System.out.println("Show<List<Integer>>: " + result);
  }
}
