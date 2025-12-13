package com.garciat.typeclasses.types;

import static com.garciat.typeclasses.api.TypeClass.Witness.Overlap.OVERLAPPING;

import com.garciat.typeclasses.api.hkt.Kind;
import com.garciat.typeclasses.api.hkt.Kind.KArr;
import com.garciat.typeclasses.api.hkt.Kind.KStar;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.classes.Applicative;
import com.garciat.typeclasses.classes.Foldable;
import com.garciat.typeclasses.classes.Functor;
import com.garciat.typeclasses.classes.Monad;
import com.garciat.typeclasses.classes.Monoid;
import com.garciat.typeclasses.classes.Show;
import com.garciat.typeclasses.classes.Traversable;
import com.garciat.typeclasses.classes.TyEq;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface FwdList<A> extends TApp<FwdList.Tag, A> {
  record Nil<A>() implements FwdList<A> {}

  record Cons<A>(A head, FwdList<A> tail) implements FwdList<A> {}

  default <R> R match(Supplier<R> onNil, BiFunction<A, FwdList<A>, R> onCons) {
    return switch (this) {
      case Nil<A>() -> onNil.get();
      case Cons<A>(A head, FwdList<A> tail) -> onCons.apply(head, tail);
    };
  }

  default <M> M foldMap(Monoid<M> monoid, Function<A, M> f) {
    return match(
        monoid::identity, (head, tail) -> monoid.combine(f.apply(head), tail.foldMap(monoid, f)));
  }

  default <B> B foldr(B identity, BiFunction<A, B, B> f) {
    return match(() -> identity, (head, tail) -> f.apply(head, tail.foldr(identity, f)));
  }

  default <B> B foldl(B identity, BiFunction<B, A, B> f) {
    return match(() -> identity, (head, tail) -> tail.foldl(f.apply(identity, head), f));
  }

  default void forEach(Consumer<A> action) {
    this.<Void>match(
        () -> null,
        (head, tail) -> {
          action.accept(head);
          tail.forEach(action);
          return null;
        });
  }

  default <B> FwdList<B> map(Function<A, B> f) {
    return match(FwdList::of, (head, tail) -> cons(f.apply(head), tail.map(f)));
  }

  default <B> FwdList<B> flatMap(Function<A, FwdList<B>> f) {
    return match(FwdList::of, (head, tail) -> append(f.apply(head), tail.flatMap(f)));
  }

  default <T extends Kind<KArr<KStar>>, B> TApp<T, FwdList<B>> traverse(
      Applicative<T> applicative, Function<A, ? extends TApp<T, B>> f) {
    return foldr(
        applicative.pure(FwdList.of()),
        (head, tailT) ->
            applicative.lift((B h, FwdList<B> t) -> cons(h, t)).apply(f.apply(head), tailT));
  }

  static <A> FwdList<A> append(FwdList<A> list1, FwdList<A> list2) {
    return list1.match(() -> list2, (head, tail) -> cons(head, append(tail, list2)));
  }

  static <A> FwdList<A> of() {
    return new Nil<>();
  }

  static <A> FwdList<A> cons(A head, FwdList<A> tail) {
    return new Cons<>(head, tail);
  }

  static <A> FwdList<A> of(A a) {
    return cons(a, of());
  }

  @SafeVarargs
  static <A> FwdList<A> of(A... items) {
    return of(Arrays.asList(items));
  }

  static <A> FwdList<A> of(Iterable<A> iter) {
    return unfoldr(
        iter.iterator(), it -> it.hasNext() ? Maybe.just(Pair.of(it.next(), it)) : Maybe.nothing());
  }

  static FwdList<Character> of(CharSequence str) {
    return unfoldr(
        0,
        index ->
            index < str.length()
                ? Maybe.just(Pair.of(str.charAt(index), index + 1))
                : Maybe.nothing());
  }

  default String toStr(TyEq<A, Character> ty) {
    StringBuilder sb = new StringBuilder();
    forEach(ch -> sb.append((char) ty.castL(ch)));
    return sb.toString();
  }

  static <A, B> FwdList<A> unfoldr(B seed, Function<B, Maybe<Pair<A, B>>> f) {
    Maybe<Pair<A, B>> result = f.apply(seed);
    return result.fold(FwdList::of, pair -> cons(pair.fst(), unfoldr(pair.snd(), f)));
  }

  public static <A> FwdList<A> build(Builder<A> builder) {
    return builder.apply(FwdList::cons, FwdList::of);
  }

  public interface Builder<A> {
    <B> B apply(BiFunction<A, B, B> cons, Supplier<B> nil);
  }

  @com.garciat.typeclasses.api.TypeClass.Witness
  static <A> Show<FwdList<A>> show(Show<A> showA) {
    return list -> {
      StringBuilder sb = new StringBuilder();
      sb.append("[");
      list.forEach(a -> sb.append(showA.show(a)));
      sb.append("]");
      return sb.toString();
    };
  }

  @com.garciat.typeclasses.api.TypeClass.Witness(overlap = OVERLAPPING)
  static Show<FwdList<Character>> show() {
    return list -> {
      StringBuilder sb = new StringBuilder();
      sb.append("\"");
      list.forEach(sb::append);
      sb.append("\"");
      return sb.toString();
    };
  }

  @com.garciat.typeclasses.api.TypeClass.Witness
  static Functor<Tag> functor() {
    return new Control();
  }

  @com.garciat.typeclasses.api.TypeClass.Witness
  static Foldable<Tag> foldable() {
    return new Control();
  }

  @com.garciat.typeclasses.api.TypeClass.Witness
  static Traversable<Tag> traversable() {
    return new Control();
  }

  @com.garciat.typeclasses.api.TypeClass.Witness
  static Applicative<Tag> applicative() {
    return new Control();
  }

  @com.garciat.typeclasses.api.TypeClass.Witness
  static Monad<Tag> monad() {
    return new Control();
  }

  final class Control implements Traversable<Tag>, Monad<Tag> {
    @Override
    public <A, B> TApp<Tag, B> map(Function<A, B> f, TApp<Tag, A> fa) {
      return unwrap(fa).map(f);
    }

    @Override
    public <A, M> M foldMap(Monoid<M> monoid, Function<A, M> f, TApp<Tag, A> ta) {
      return unwrap(ta).foldMap(monoid, f);
    }

    @Override
    public <F extends Kind<KArr<KStar>>, A, B> TApp<F, ? extends TApp<Tag, B>> traverse(
        Applicative<F> applicative, Function<A, ? extends TApp<F, B>> f, TApp<Tag, A> ta) {
      return unwrap(ta).traverse(applicative, f);
    }

    @Override
    public <A> TApp<Tag, A> pure(A a) {
      return FwdList.of(a);
    }

    @Override
    public <A, B> TApp<Tag, B> flatMap(Function<A, TApp<Tag, B>> f, TApp<Tag, A> fa) {
      return unwrap(fa).flatMap(a -> unwrap(f.apply(a)));
    }
  }

  final class Tag extends TagBase<KArr<KStar>> {}

  static <A> FwdList<A> unwrap(TApp<Tag, A> value) {
    return (FwdList<A>) value;
  }
}
