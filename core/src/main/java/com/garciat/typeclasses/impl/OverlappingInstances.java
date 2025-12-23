package com.garciat.typeclasses.impl;

import static com.garciat.typeclasses.api.TypeClass.Witness.Overlap.OVERLAPPABLE;
import static com.garciat.typeclasses.api.TypeClass.Witness.Overlap.OVERLAPPING;

import java.util.List;

public final class OverlappingInstances {
  private OverlappingInstances() {}

  /**
   * @implSpec <a href=
   *     "https://ghc.gitlab.haskell.org/ghc/doc/users_guide/exts/instances.html#overlapping-instances">6.8.8.5.
   *     Overlapping instances</a>
   */
  public static <M, V, C, P> List<Match<M, V, C, P>> reduce(List<Match<M, V, C, P>> candidates) {
    return candidates.stream()
        .filter(
            iX ->
                candidates.stream().filter(iY -> iX != iY).noneMatch(iY -> isOverlappedBy(iX, iY)))
        .toList();
  }

  private static <M, V, C, P> boolean isOverlappedBy(Match<M, V, C, P> iX, Match<M, V, C, P> iY) {
    return (iX.ctor().overlap() == OVERLAPPABLE || iY.ctor().overlap() == OVERLAPPING)
        && isSubstitutionInstance(iX, iY)
        && !isSubstitutionInstance(iY, iX);
  }

  private static <M, V, C, P> boolean isSubstitutionInstance(
      Match<M, V, C, P> base, Match<M, V, C, P> reference) {
    return Unification.unify(base.ctor().returnType(), reference.ctor().returnType())
        .fold(() -> false, map -> !map.isEmpty());
  }
}
