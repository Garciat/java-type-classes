package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.Kind;
import com.garciat.typeclasses.api.hkt.Kind.KArr;
import com.garciat.typeclasses.api.hkt.Kind.KStar;
import com.garciat.typeclasses.api.hkt.TApp;

@TypeClass
public interface Alternative<F extends Kind<KArr<KStar>>> extends Applicative<F> {
  <A> TApp<F, A> empty();

  <A> TApp<F, A> alt(TApp<F, A> fa1, TApp<F, A> fa2);
}
