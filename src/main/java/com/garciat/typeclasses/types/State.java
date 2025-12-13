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
import java.util.function.Function;

@FunctionalInterface
public interface State<S, A> extends TApp<TPar<State.Tag, S>, A> {
  Pair<A, S> run(S state);

  static <S, A> State<S, A> of(Function<S, Pair<A, S>> f) {
    return f::apply;
  }

  static <S, A> State<S, A> pure(A a) {
    return state -> Pair.of(a, state);
  }

  default <B> State<S, B> map(Function<A, B> f) {
    return state -> run(state).mapFst(f);
  }

  default <B> State<S, B> flatMap(Function<A, State<S, B>> f) {
    return state ->
        switch (run(state)) {
          case Pair<A, S>(A a, S newState) -> f.apply(a).run(newState);
        };
  }

  @Witness
  static <S> Functor<TPar<Tag, S>> functor() {
    return new Functor<>() {
      @Override
      public <A, B> TApp<TPar<Tag, S>, B> map(Function<A, B> f, TApp<TPar<Tag, S>, A> fa) {
        return unwrap(fa).map(f);
      }
    };
  }

  @Witness
  static <S> Applicative<TPar<Tag, S>> applicative() {
    return new Applicative<>() {
      @Override
      public <A> TApp<TPar<Tag, S>, A> pure(A a) {
        return State.pure(a);
      }

      @Override
      public <A, B> TApp<TPar<Tag, S>, B> ap(
          TApp<TPar<Tag, S>, Function<A, B>> ff, TApp<TPar<Tag, S>, A> fa) {
        return unwrap(ff).flatMap(f -> unwrap(fa).map(f));
      }
    };
  }

  @Witness
  static <S> Monad<TPar<Tag, S>> monad() {
    return new Monad<>() {
      @Override
      public <A> TApp<TPar<Tag, S>, A> pure(A a) {
        return State.pure(a);
      }

      @Override
      public <A, B> TApp<TPar<Tag, S>, B> flatMap(
          Function<A, TApp<TPar<Tag, S>, B>> f, TApp<TPar<Tag, S>, A> fa) {
        return unwrap(fa).flatMap(a -> unwrap(f.apply(a)));
      }
    };
  }

  final class Tag extends TagBase<KArr<KArr<KStar>>> {}

  static <S, A> State<S, A> unwrap(TApp<TPar<Tag, S>, A> value) {
    return (State<S, A>) value;
  }
}
