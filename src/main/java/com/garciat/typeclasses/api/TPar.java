package com.garciat.typeclasses.api;

/**
 * Partial application of a binary type constructor.
 *
 * <p>TPar :: (* -> * -> *) -> * -> (* -> *)
 *
 * <p><b>PUBLIC API</b>: This is part of the library's public API. Users will use this in type
 * signatures.
 *
 * @param <Tag> the type constructor tag
 * @param <A> the first applied type argument
 */
public interface TPar<Tag extends Kind<Kind.KArr<Kind.KArr<Kind.KStar>>>, A>
    extends Kind<Kind.KArr<Kind.KStar>> {}
