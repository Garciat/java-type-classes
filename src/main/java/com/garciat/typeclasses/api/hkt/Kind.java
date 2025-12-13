package com.garciat.typeclasses.api.hkt;

/** This is how we get basic kind checking in Java */
public interface Kind<K extends Kind.Base> {
  sealed interface Base {}

  /** KStar = * */
  final class KStar implements Base {}

  /** KArr k = * -> k */
  final class KArr<K extends Base> implements Base {}
}
