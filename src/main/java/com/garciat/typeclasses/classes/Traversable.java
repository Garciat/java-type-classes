package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.Kind;
import com.garciat.typeclasses.api.hkt.Kind.KArr;
import com.garciat.typeclasses.api.hkt.Kind.KStar;
import com.garciat.typeclasses.api.hkt.TApp;
import java.util.function.Function;

@TypeClass
public interface Traversable<T extends Kind<KArr<KStar>>> extends Functor<T>, Foldable<T> {
  <F extends Kind<KArr<KStar>>, A, B> TApp<F, ? extends TApp<T, B>> traverse(
      Applicative<F> applicative, Function<A, ? extends TApp<F, B>> f, TApp<T, A> ta);

  static <F extends Kind<KArr<KStar>>, T extends Kind<KArr<KStar>>, A, B>
      TApp<F, ? extends TApp<T, B>> traverse(
          Traversable<T> traversable,
          Applicative<F> applicative,
          TApp<T, A> tA,
          Function<A, TApp<F, B>> f) {
    return traversable.traverse(applicative, f, tA);
  }
}
