package com.garciat.typeclasses.api;

/**
 * Base interface for kind-level types, providing basic kind checking in Java.
 *
 * <p>This interface is used to represent type-level kinds, similar to kinds in Haskell's type
 * system.
 *
 * <p><b>PUBLIC API</b>: This is part of the library's public API. Users implementing custom data
 * types will need to use this interface.
 */
public interface Kind<K extends Kind.Base> {
  /** Base interface for all kinds. */
  sealed interface Base permits KStar, KArr {}

  /** KStar represents the kind * (star) - the kind of proper types. */
  final class KStar implements Base {}

  /** KArr k represents the kind * -> k - the kind of type constructors. */
  final class KArr<K extends Base> implements Base {}
}
