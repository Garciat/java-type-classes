package com.garciat.typeclasses.classes;

import static com.garciat.typeclasses.utils.Functions.curry;
import static com.garciat.typeclasses.utils.Functions.flip;
import static java.util.function.Function.identity;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.Kind;
import com.garciat.typeclasses.api.hkt.Kind.KArr;
import com.garciat.typeclasses.api.hkt.Kind.KStar;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.types.FwdList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

// class Foldable t where
@TypeClass
public interface Foldable<T extends Kind<KArr<KStar>>> {
  // foldMap :: Monoid m => (a -> m) -> t a -> m
  <A, M> M foldMap(Monoid<M> monoid, Function<A, M> f, TApp<T, A> ta);

  // fold :: Monoid m => t m -> m
  default <A> A fold(TApp<T, A> ta, Monoid<A> monoid) {
    return foldMap(monoid, identity(), ta);
  }

  // foldr :: (a -> b -> b) -> b -> t a -> b
  // foldr f z t = appEndo (foldMap (Endo . f) t) z
  default <A, B> B foldr(BiFunction<A, B, B> f, B z, TApp<T, A> t) {
    Endo<B> endo = foldMap(Endo.monoid(), curry(f).andThen(Endo::of), t);
    return endo.appEndo().apply(z);
  }

  // foldl :: (b -> a -> b) -> b -> t a -> b
  // foldl f z t = appEndo (getDual (foldMap (Dual . Endo . flip f) t)) z
  default <A, B> B foldl(BiFunction<B, A, B> f, B z, TApp<T, A> t) {
    Dual<Endo<B>> dualEndo =
        foldMap(Dual.monoid(Endo.monoid()), curry(flip(f)).andThen(Endo::of).andThen(Dual::of), t);
    return dualEndo.getDual().appEndo().apply(z);
  }

  // toList :: t a -> [a]
  default <A> FwdList<A> toList(TApp<T, A> ta) {
    return FwdList.build(
        new FwdList.Builder<>() {
          @Override
          public <B> B apply(BiFunction<A, B, B> cons, Supplier<B> nil) {
            return foldr(cons, nil.get(), ta);
          }
        });
  }

  // null :: t a -> Bool
  default <A> boolean isEmpty(TApp<T, A> ta) {
    return foldr((a, b) -> false, true, ta);
  }

  // length :: t a -> Int
  default <A> int length(TApp<T, A> ta) {
    return foldl((n, a) -> n + 1, 0, ta);
  }
}

record Endo<A>(Function<A, A> appEndo) {
  public Endo<A> compose(Endo<A> other) {
    return new Endo<>(a -> appEndo.apply(other.appEndo.apply(a)));
  }

  public static <A> Endo<A> id() {
    return new Endo<>(a -> a);
  }

  public static <A> Endo<A> of(Function<A, A> f) {
    return new Endo<>(f);
  }

  public static <A, B> Function<A, Endo<B>> of(BiFunction<A, B, B> f) {
    return a -> new Endo<>(b -> f.apply(a, b));
  }

  @TypeClass.Witness
  static <A> Monoid<Endo<A>> monoid() {
    return Monoid.of(Endo::id, Endo::compose);
  }
}

record Dual<A>(A getDual) {
  public static <A> Dual<A> of(A a) {
    return new Dual<>(a);
  }

  public static <A> Monoid<Dual<A>> monoid(Monoid<A> monoidA) {
    return Monoid.of(
        () -> new Dual<>(monoidA.identity()),
        (d1, d2) -> new Dual<>(monoidA.combine(d2.getDual, d1.getDual)));
  }
}
