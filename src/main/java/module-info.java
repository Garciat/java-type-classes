/**
 * Java Type Classes Library - A type class system for Java inspired by Haskell.
 *
 * <p>This module provides type classes and functional programming abstractions for Java.
 *
 * <h2>Exported Packages</h2>
 *
 * <ul>
 *   <li>{@code com.garciat.typeclasses.api} - Core type class infrastructure (Kind, TApp, TPar,
 *       TagBase, TypeClass, Ty, Ctx)
 *   <li>{@code com.garciat.typeclasses} - All type classes and data types (for backward
 *       compatibility and current use)
 * </ul>
 *
 * <p>The {@code com.garciat.typeclasses.impl} package contains internal implementation details and
 * is not exported. The {@code classes} and {@code types} packages are reserved for future
 * reorganization.
 */
module com.garciat.typeclasses {
  // Export public API packages
  exports com.garciat.typeclasses.api;

  // Export main package containing all type classes and data types
  exports com.garciat.typeclasses;

// impl package is NOT exported - it contains internal implementation details
// classes and types packages are reserved for future complete reorganization
}
