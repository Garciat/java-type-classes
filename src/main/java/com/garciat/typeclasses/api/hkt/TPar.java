package com.garciat.typeclasses.api.hkt;

import com.garciat.typeclasses.api.hkt.Kind.KArr;
import com.garciat.typeclasses.api.hkt.Kind.KStar;

/**
 * Partial application of a binary type constructor.
 *
 * <p>TPar :: (* -> * -> *) -> * -> (* -> *)
 */
public interface TPar<Tag extends Kind<KArr<KArr<KStar>>>, A> extends Kind<KArr<KStar>> {}
