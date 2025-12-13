package com.garciat.typeclasses.classes;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.api.hkt.Kind;
import com.garciat.typeclasses.api.hkt.Kind.KArr;
import com.garciat.typeclasses.api.hkt.Kind.KStar;
import com.garciat.typeclasses.api.hkt.TApp;
import java.util.function.Function;

@TypeClass
public interface Functor<F extends Kind<KArr<KStar>>> {
  <A, B> TApp<F, B> map(Function<A, B> f, TApp<F, A> fa);
}
