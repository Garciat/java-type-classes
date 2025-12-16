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
  public static List<WitnessConstructor> reduce(List<WitnessConstructor> candidates) {
    return candidates.stream()
        .filter(
            iX ->
                candidates.stream().filter(iY -> iX != iY).noneMatch(iY -> isOverlappedBy(iX, iY)))
        .toList();
  }

  private static boolean isOverlappedBy(WitnessConstructor iX, WitnessConstructor iY) {
    return (iX.overlap() == OVERLAPPABLE || iY.overlap() == OVERLAPPING)
        && isSubstitutionInstance(iX, iY)
        && !isSubstitutionInstance(iY, iX);
  }

  private static boolean isSubstitutionInstance(
      WitnessConstructor base, WitnessConstructor reference) {
    return Unification.unify(base.returnType(), reference.returnType())
        .fold(() -> false, map -> !map.isEmpty());
  }
}
