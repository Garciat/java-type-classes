package com.garciat.typeclasses.types;

import com.garciat.typeclasses.api.TypeClass.Witness;
import com.garciat.typeclasses.api.hkt.Kind.KArr;
import com.garciat.typeclasses.api.hkt.Kind.KStar;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TPar;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.classes.Applicative;
import com.garciat.typeclasses.classes.Functor;
import com.garciat.typeclasses.classes.Monad;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

public sealed interface Either<L, R> extends TApp<TPar<Either.Tag, L>, R> {
  record Left<L, R>(L value) implements Either<L, R> {}

  record Right<L, R>(R value) implements Either<L, R> {}

  static <L, R> Either<L, R> left(L value) {
    return new Left<>(value);
  }

  static <L, R> Either<L, R> right(R value) {
    return new Right<>(value);
  }

  static <A> Either<Exception, A> call(Callable<A> callable) {
    try {
      return right(callable.call());
    } catch (Exception e) {
      return left(e);
    }
  }

  default <X> Either<L, X> map(Function<? super R, ? extends X> f) {
    return fold(Either::left, f.andThen(Either::right));
  }

  default <X> Either<X, R> mapLeft(Function<? super L, ? extends X> f) {
    return fold(f.andThen(Either::left), Either::right);
  }

  default <X> Either<L, X> flatMap(Function<? super R, ? extends Either<L, X>> f) {
    return fold(Either::left, f);
  }

  default <A> A fold(
      Function<? super L, ? extends A> fLeft, Function<? super R, ? extends A> fRight) {
    return switch (this) {
      case Left<L, R>(L value) -> fLeft.apply(value);
      case Right<L, R>(R value) -> fRight.apply(value);
    };
  }

  static <A, L, R> Either<L, List<R>> traverse(List<A> list, Function<? super A, Either<L, R>> f) {
    return unwrap(JavaList.of(list).traverse(Either.applicative(), f::apply)).map(JavaList::toList);
  }

  @Witness
  static <L> Functor<TPar<Tag, L>> functor() {
    return new Functor<>() {
      @Override
      public <A, B> TApp<TPar<Tag, L>, B> map(Function<A, B> f, TApp<TPar<Tag, L>, A> fa) {
        return unwrap(fa).map(f);
      }
    };
  }

  @Witness
  static <L> Applicative<TPar<Tag, L>> applicative() {
    return monad();
  }

  @Witness
  static <L> Monad<TPar<Tag, L>> monad() {
    return new Monad<>() {
      @Override
      public <A> TApp<TPar<Tag, L>, A> pure(A a) {
        return right(a);
      }

      @Override
      public <A, B> TApp<TPar<Tag, L>, B> flatMap(
          Function<A, TApp<TPar<Tag, L>, B>> f, TApp<TPar<Tag, L>, A> fa) {
        return unwrap(fa).flatMap(a -> unwrap(f.apply(a)));
      }
    };
  }

  final class Tag extends TagBase<KArr<KArr<KStar>>> {}

  static <L, R> Either<L, R> unwrap(TApp<TPar<Tag, L>, R> value) {
    return (Either<L, R>) value;
  }
}
