package com.garciat.typeclasses.examples;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.assertj.core.api.Assertions.assertThat;

import com.garciat.typeclasses.api.Out;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.api.TypeClass;
import java.util.List;
import org.junit.jupiter.api.Test;

/// Based on:
///
/// ```haskell
/// type family ElementOf a where
///   ElementOf [[a]] = ElementOf [a]
///   ElementOf [a]   = a
///
/// class Flatten a where
///   flatten :: a -> [ElementOf a]
///
/// instance Flatten [a] where
///   flatten x = x
///
/// instance {-# OVERLAPPING #-} Flatten [a] => Flatten [[a]] where
///   flatten x = flatten (concat x)
/// ```
///
/// From: "An introduction to typeclass metaprogramming" by Alexis King
public class Example4 {
  @Test
  void main() {
    Flatten<List<List<String>>, String> e1 = witness(new Ty<>() {});
    Flatten<List<String>, String> e2 = witness(new Ty<>() {});

    assertThat(e1.flatten(List.of(List.of("a", "b"), List.of("c"))))
        .isEqualTo(List.of("a", "b", "c"));
    assertThat(e2.flatten(List.of("a", "b", "c"))).isEqualTo(List.of("a", "b", "c"));
  }

  @TypeClass
  public interface Flatten<A, @Out T> {
    List<T> flatten(A list);

    @TypeClass.Witness
    static <A, T> Flatten<List<A>, T> here(TyEq<A, T> eq) {
      return list -> list.stream().map(eq::castLeft).toList();
    }

    @TypeClass.Witness(overlap = TypeClass.Witness.Overlap.OVERLAPPING)
    static <A, @Out R> Flatten<List<List<A>>, R> there(Flatten<List<A>, R> e) {
      return list -> list.stream().flatMap(innerList -> e.flatten(innerList).stream()).toList();
    }
  }

  @TypeClass
  public interface TyEq<A, B> {
    B castLeft(A a);

    A castRight(B b);

    @TypeClass.Witness
    static <A> TyEq<A, A> refl() {
      return new TyEq<>() {
        @Override
        public A castLeft(A a) {
          return a;
        }

        @Override
        public A castRight(A a) {
          return a;
        }
      };
    }
  }
}
