package com.garciat.typeclasses.api.hkt;

import com.garciat.typeclasses.api.hkt.Kind.KArr;
import com.garciat.typeclasses.api.hkt.Kind.KStar;

/**
 * Full application of a unary type constructor.
 *
 * <p>TApp :: (* -> *) -> * -> *
 */
public interface TApp<Tag extends Kind<KArr<KStar>>, A> extends Kind<KStar> {}
