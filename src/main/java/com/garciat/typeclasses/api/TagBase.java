package com.garciat.typeclasses.api;

/**
 * Base class for type-level tags. Subclasses of this class represent type constructor tags used in
 * higher-kinded type encoding.
 *
 * <p><b>PUBLIC API</b>: This is part of the library's public API. Users implementing custom data
 * types will need to extend this class.
 *
 * @param <K> the kind of this tag
 */
public abstract class TagBase<K extends Kind.Base> implements Kind<K> {}
