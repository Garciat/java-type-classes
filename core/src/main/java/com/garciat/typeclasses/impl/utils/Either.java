package com.garciat.typeclasses.impl.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

public sealed interface Either<L, R> {
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
    List<R> result = new ArrayList<>();
    for (A a : list) {
      switch (f.apply(a)) {
        case Left(L value) -> {
          return left(value);
        }
        case Right(R value) -> {
          result.add(value);
        }
      }
    }
    return right(result);
  }
}
