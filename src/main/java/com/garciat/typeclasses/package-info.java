/**
 * Java Type Classes Library
 *
 * <p>This library provides a type class system for Java, inspired by Haskell's type classes. It
 * allows you to define type classes (interfaces with @TypeClass annotation) and automatically
 * resolve instances for various types.
 *
 * <h2>Public API</h2>
 *
 * <p>The main entry point is {@link com.garciat.typeclasses.TypeClasses#witness}, which resolves
 * type class instances:
 *
 * <pre>{@code
 * Show<List<Integer>> showListInt = TypeClasses.witness(new Ty<>() {});
 * String result = showListInt.show(List.of(1, 2, 3));
 * }</pre>
 *
 * <h3>Core Type Classes</h3>
 *
 * <ul>
 *   <li>{@link com.garciat.typeclasses.Show} - Convert values to strings
 *   <li>{@link com.garciat.typeclasses.Eq} - Equality testing
 *   <li>{@link com.garciat.typeclasses.Ord} - Ordering comparisons
 *   <li>{@link com.garciat.typeclasses.Monoid} - Associative binary operations with identity
 *   <li>{@link com.garciat.typeclasses.Functor} - Mappable type constructors
 *   <li>{@link com.garciat.typeclasses.Applicative} - Application of functions in a context
 *   <li>{@link com.garciat.typeclasses.Monad} - Sequential composition of computations
 *   <li>{@link com.garciat.typeclasses.Foldable} - Structures that can be folded
 *   <li>{@link com.garciat.typeclasses.Traversable} - Structures that can be traversed
 * </ul>
 *
 * <h3>Data Types</h3>
 *
 * <ul>
 *   <li>{@link com.garciat.typeclasses.Maybe} - Optional values
 *   <li>{@link com.garciat.typeclasses.Either} - Sum types (Left or Right)
 *   <li>{@link com.garciat.typeclasses.JavaList} - List with type class instances
 *   <li>{@link com.garciat.typeclasses.FwdList} - Functional forward list
 *   <li>{@link com.garciat.typeclasses.Parser} - Parser combinators
 *   <li>{@link com.garciat.typeclasses.State} - State monad
 * </ul>
 *
 * <h3>Type System Infrastructure</h3>
 *
 * <ul>
 *   <li>{@link com.garciat.typeclasses.Kind} - Kind system for higher-kinded types
 *   <li>{@link com.garciat.typeclasses.TApp} - Type application
 *   <li>{@link com.garciat.typeclasses.TPar} - Partial type application
 *   <li>{@link com.garciat.typeclasses.TagBase} - Base class for type tags
 * </ul>
 *
 * <h2>Examples</h2>
 *
 * <p>See {@link com.garciat.typeclasses.Examples} for usage examples.
 */
package com.garciat.typeclasses;
