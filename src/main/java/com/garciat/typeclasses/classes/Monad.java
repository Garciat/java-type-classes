package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.Kind;
import com.garciat.typeclasses.api.hkt.Kind.KArr;
import com.garciat.typeclasses.api.hkt.Kind.KStar;
import com.garciat.typeclasses.api.hkt.TApp;
import java.util.function.Function;

@TypeClass
public interface Monad<M extends Kind<KArr<KStar>>> extends Applicative<M> {
  <A, B> TApp<M, B> flatMap(Function<A, TApp<M, B>> f, TApp<M, A> fa);

  @Override
  default <A, B> TApp<M, B> map(Function<A, B> f, TApp<M, A> fa) {
    return flatMap(a -> pure(f.apply(a)), fa);
  }

  @Override
  default <A, B> TApp<M, B> ap(TApp<M, Function<A, B>> ff, TApp<M, A> fa) {
    return flatMap(a -> map(f -> f.apply(a), ff), fa);
  }
}
