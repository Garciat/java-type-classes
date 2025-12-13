package com.garciat.typeclasses.api;

public interface Kind<K extends Kind.Base> {
  sealed interface Base permits KStar, KArr {}

  final class KStar implements Base {}

  final class KArr<K extends Base> implements Base {}
}
