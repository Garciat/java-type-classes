package com.garciat.typeclasses.types;

import com.garciat.typeclasses.api.TypeClass.Witness;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.classes.Applicative;
import com.garciat.typeclasses.classes.Functor;
import com.garciat.typeclasses.classes.Monad;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public sealed interface Maybe<A> extends TApp<Maybe.Tag, A> {
  record Just<A>(A value) implements Maybe<A> {}

  record Nothing<A>() implements Maybe<A> {}

  static <A> Maybe<A> just(A value) {
    return new Just<>(value);
  }

  static <A> Maybe<A> nothing() {
    return new Nothing<>();
  }

  default <R> R fold(Supplier<R> onNothing, Function<A, R> onJust) {
    return switch (this) {
      case Just<A>(A value) -> onJust.apply(value);
      case Nothing<A>() -> onNothing.get();
    };
  }

  default Maybe<A> filter(Function<A, Boolean> predicate) {
    return flatMap(a -> predicate.apply(a) ? just(a) : nothing());
  }

  default Stream<A> stream() {
    return fold(Stream::empty, Stream::of);
  }

  default <B> Maybe<B> map(Function<A, B> f) {
    return fold(Maybe::nothing, a -> just(f.apply(a)));
  }

  default <B> Maybe<B> flatMap(Function<A, Maybe<B>> f) {
    return switch (this) {
      case Just<A>(A value) -> f.apply(value);
      case Nothing<A>() -> nothing();
    };
  }

  static <A, B, C> BiFunction<Maybe<A>, Maybe<B>, Maybe<C>> lift(BiFunction<A, B, C> f) {
    return (ma, mb) -> ma.flatMap(a -> mb.map(b -> f.apply(a, b)));
  }

  static <A, B, C> Maybe<C> apply(BiFunction<A, B, C> f, Maybe<A> ma, Maybe<B> mb) {
    return lift(f).apply(ma, mb);
  }

  static <A, B> List<B> mapMaybe(List<A> as, Function<A, Maybe<B>> f) {
    return as.stream().map(f).flatMap(Maybe::stream).toList();
  }

  @Witness
  static Functor<Tag> functor() {
    return new Functor<>() {
      @Override
      public <A, B> TApp<Tag, B> map(Function<A, B> f, TApp<Tag, A> fa) {
        return unwrap(fa).map(f);
      }
    };
  }

  @Witness
  static Applicative<Tag> applicative() {
    return new Applicative<>() {
      @Override
      public <A> TApp<Tag, A> pure(A a) {
        return just(a);
      }

      @Override
      public <A, B> TApp<Tag, B> ap(TApp<Tag, Function<A, B>> ff, TApp<Tag, A> fa) {
        return unwrap(ff).flatMap(f -> unwrap(fa).map(f));
      }
    };
  }

  @Witness
  static Monad<Tag> monad() {
    return new Monad<>() {
      @Override
      public <A> TApp<Tag, A> pure(A a) {
        return just(a);
      }

      @Override
      public <A, B> TApp<Tag, B> flatMap(Function<A, TApp<Tag, B>> f, TApp<Tag, A> fa) {
        return unwrap(fa).flatMap(a -> unwrap(f.apply(a)));
      }
    };
  }

  final class Tag extends TagBase<KArr<KStar>> {}

  static <A> Maybe<A> unwrap(TApp<Tag, A> value) {
    return (Maybe<A>) value;
  }
}
