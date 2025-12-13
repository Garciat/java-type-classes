package com.garciat.typeclasses;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.junit.jupiter.api.Assertions.*;

import com.garciat.typeclasses.api.TApp;
import com.garciat.typeclasses.api.Ty;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests demonstrating the library's public API usage. */
final class LibraryUsageTest {

  @Test
  void canResolveShowForBasicTypes() {
    Show<Integer> showInt = witness(new Ty<>() {});
    assertEquals("42", showInt.show(42));

    Show<String> showString = witness(new Ty<>() {});
    assertEquals("\"hello\"", showString.show("hello"));
  }

  @Test
  void canResolveShowForComplexTypes() {
    Show<List<Integer>> showListInt = witness(new Ty<>() {});
    String result = showListInt.show(List.of(1, 2, 3));
    assertEquals("[1, 2, 3]", result);
  }

  @Test
  void canResolveShowForNestedTypes() {
    Show<Optional<List<String>>> showOptListStr = witness(new Ty<>() {});
    String result = showOptListStr.show(Optional.of(List.of("a", "b")));
    assertEquals("Some([\"a\", \"b\"])", result);
  }

  @Test
  void canResolveEqForBasicTypes() {
    Eq<Integer> eqInt = witness(new Ty<>() {});
    assertTrue(eqInt.eq(42, 42));
    assertFalse(eqInt.eq(42, 43));
  }

  @Test
  void canResolveEqForComplexTypes() {
    Eq<List<String>> eqListStr = witness(new Ty<>() {});
    assertTrue(eqListStr.eq(List.of("a", "b"), List.of("a", "b")));
    assertFalse(eqListStr.eq(List.of("a", "b"), List.of("a", "c")));
  }

  @Test
  void canResolveOrdForBasicTypes() {
    Ord<Integer> ordInt = witness(new Ty<>() {});
    assertEquals(Ordering.LT, ordInt.compare(1, 2));
    assertEquals(Ordering.EQ, ordInt.compare(2, 2));
    assertEquals(Ordering.GT, ordInt.compare(3, 2));
  }

  @Test
  void canResolveMonoidForString() {
    Monoid<String> monoidString = witness(new Ty<>() {});
    assertEquals("", monoidString.identity());
    assertEquals("ab", monoidString.combine("a", "b"));
  }

  @Test
  void canUseMonoidCombineAll() {
    List<String> strings = List.of("Hello", " ", "World");
    String result = Monoid.combineAll(witness(new Ty<>() {}), strings);
    assertEquals("Hello World", result);
  }

  @Test
  void canUseFunctorMap() {
    Functor<Maybe.Tag> functorMaybe = witness(new Ty<>() {});
    TApp<Maybe.Tag, Integer> maybeInt = Maybe.just(42);
    TApp<Maybe.Tag, String> maybeStr = functorMaybe.map(Object::toString, maybeInt);
    assertEquals("42", Maybe.unwrap(maybeStr).fold(() -> null, x -> x));
  }

  @Test
  void canUseApplicativePure() {
    Applicative<Maybe.Tag> appMaybe = witness(new Ty<>() {});
    TApp<Maybe.Tag, Integer> maybeInt = appMaybe.pure(42);
    Integer unwrapped = Maybe.unwrap(maybeInt).fold(() -> null, x -> x);
    assertEquals(42, unwrapped.intValue());
  }

  @Test
  void canUseMonadFlatMap() {
    Monad<Maybe.Tag> monadMaybe = witness(new Ty<>() {});
    TApp<Maybe.Tag, Integer> maybeInt = Maybe.just(42);
    TApp<Maybe.Tag, Integer> result = monadMaybe.flatMap(x -> Maybe.just(x * 2), maybeInt);
    Integer unwrapped = Maybe.unwrap(result).fold(() -> null, x -> x);
    assertEquals(84, unwrapped.intValue());
  }

  @Test
  void canUseFoldableLength() {
    Foldable<FwdList.Tag> foldableFwdList = witness(new Ty<>() {});
    FwdList<Integer> list = FwdList.of(1, 2, 3, 4, 5);
    assertEquals(5, (int) foldableFwdList.length(list));
  }
}
