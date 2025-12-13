package com.garciat.typeclasses.api;

/**
 * Full application of a unary type constructor.
 *
 * <p>TApp :: (* -> *) -> * -> *
 *
 * <p><b>PUBLIC API</b>: This is part of the library's public API. Users will use this in type
 * signatures.
 *
 * @param <Tag> the type constructor tag
 * @param <A> the applied type argument
 */
public interface TApp<Tag extends Kind<Kind.KArr<Kind.KStar>>, A> extends Kind<Kind.KStar> {}
