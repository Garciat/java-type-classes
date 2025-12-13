package com.garciat.typeclasses.classes;

import static java.util.function.Function.identity;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.Kind;
import com.garciat.typeclasses.api.hkt.Kind.KArr;
import com.garciat.typeclasses.api.hkt.Kind.KStar;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.types.FwdList;
import com.garciat.typeclasses.types.JavaList;
import java.util.function.BiFunction;
import java.util.function.Function;

@TypeClass
public interface Applicative<F extends Kind<KArr<KStar>>> extends Functor<F> {
  <A> TApp<F, A> pure(A a);

  <A, B> TApp<F, B> ap(TApp<F, Function<A, B>> ff, TApp<F, A> fa);

  @Override
  default <A, B> TApp<F, B> map(Function<A, B> f, TApp<F, A> fa) {
    return ap(pure(f), fa);
  }

  default <A, B, C> BiFunction<TApp<F, A>, TApp<F, B>, TApp<F, C>> lift(BiFunction<A, B, C> f) {
    return (fa, fb) -> ap(ap(pure(a -> b -> f.apply(a, b)), fa), fb);
  }

  default <A> TApp<F, FwdList<A>> sequence(FwdList<? extends TApp<F, A>> fas) {
    return fas.traverse(this, identity());
  }

  default <A> TApp<F, JavaList<A>> sequence(JavaList<? extends TApp<F, A>> fas) {
    return fas.traverse(this, identity());
  }
}
