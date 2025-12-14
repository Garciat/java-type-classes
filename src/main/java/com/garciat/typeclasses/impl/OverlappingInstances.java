package com.garciat.typeclasses.impl;

import static com.garciat.typeclasses.api.TypeClass.Witness.Overlap.OVERLAPPABLE;
import static com.garciat.typeclasses.api.TypeClass.Witness.Overlap.OVERLAPPING;

import com.garciat.typeclasses.impl.WitnessRule.InstanceConstructor;
import java.util.List;

public class OverlappingInstances {
  /**
   * @implSpec <a href=
   *     "https://ghc.gitlab.haskell.org/ghc/doc/users_guide/exts/instances.html#overlapping-instances">6.8.8.5.
   *     Overlapping instances</a>
   */
  public static List<InstanceConstructor> reduce(List<InstanceConstructor> candidates) {
    return candidates.stream()
        .filter(
            iX ->
                candidates.stream().filter(iY -> iX != iY).noneMatch(iY -> isOverlappedBy(iX, iY)))
        .toList();
  }

  private static boolean isOverlappedBy(InstanceConstructor iX, InstanceConstructor iY) {
    return (iX.overlap() == OVERLAPPABLE || iY.overlap() == OVERLAPPING)
        && isSubstitutionInstance(iX, iY)
        && !isSubstitutionInstance(iY, iX);
  }

  private static boolean isSubstitutionInstance(
      InstanceConstructor base, InstanceConstructor reference) {
    return Unification.unify(base.func().returnType(), reference.func().returnType())
        .fold(() -> false, map -> !map.isEmpty());
  }
}
