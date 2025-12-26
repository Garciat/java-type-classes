package com.garciat.typeclasses.examples;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.assertj.core.api.Assertions.assertThat;

import com.garciat.typeclasses.api.Out;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.impl.utils.Unit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class Example6 {
  @Disabled("Not working yet ):")
  @Test
  void test1() {
    var expr = new Expr.Add<>(new Expr.Int(1), new Expr.Add<>(new Expr.Int(2), new Expr.Int(3)));

    boolean result = ReifiedContainsVoid.containsVoid(witness(new Ty<>() {}), expr);

    assertThat(result).isFalse();
  }

  @Test
  void test2() {
    var expr = new Expr.Add<>(new Expr.Int(1), new Expr.Add<>(new Expr.Void(), new Expr.Int(3)));

    boolean result = ReifiedContainsVoid.containsVoid(witness(new Ty<>() {}), expr);

    assertThat(result).isTrue();
  }

  public sealed interface Fact {
    record True() implements Fact {}

    record False() implements Fact {}
  }

  @TypeClass
  public interface FactOr<A, B, @Out R> {
    Unit trivial();

    @TypeClass.Witness(overlap = TypeClass.Witness.Overlap.OVERLAPPING)
    static FactOr<Fact.False, Fact.False, Fact.False> here() {
      return Unit::unit;
    }

    @TypeClass.Witness
    static <A, B> FactOr<A, B, Fact.True> notHere() {
      return Unit::unit;
    }
  }

  @TypeClass
  public interface FactNot<B, @Out R> {
    Unit trivial();

    @TypeClass.Witness
    static FactNot<Fact.True, Fact.False> factNotTrue() {
      return Unit::unit;
    }

    @TypeClass.Witness
    static FactNot<Fact.False, Fact.True> factNotFalse() {
      return Unit::unit;
    }
  }

  @TypeClass
  public interface ReifiedFact<B> {
    boolean reify();

    @TypeClass.Witness
    static ReifiedFact<Fact.True> reifiedTrue() {
      return () -> true;
    }

    @TypeClass.Witness
    static ReifiedFact<Fact.False> reifiedFalse() {
      return () -> false;
    }
  }

  public sealed interface Expr<T extends Expr<T>> {
    record Void() implements Expr<Void> {}

    record Int(int value) implements Expr<Int> {}

    record Add<T1 extends Expr<T1>, T2 extends Expr<T2>>(Expr<T1> left, Expr<T2> right)
        implements Expr<Add<T1, T2>> {}
  }

  @TypeClass
  public interface ContainsVoid<E extends Expr<E>, @Out R> {
    Unit trivial();

    @TypeClass.Witness
    static ContainsVoid<Expr.Void, Fact.True> here() {
      return Unit::unit;
    }

    @TypeClass.Witness
    static ContainsVoid<Expr.Int, Fact.False> notHereInt() {
      return Unit::unit;
    }

    @TypeClass.Witness
    static <
            T1 extends Expr<T1>,
            T2 extends Expr<T2>,
            FL extends Fact,
            FR extends Fact,
            F extends Fact>
        ContainsVoid<Expr.Add<T1, T2>, F> add(
            ContainsVoid<T1, FL> left, ContainsVoid<T2, FR> right, FactOr<FL, FR, F> factOr) {
      return Unit::unit;
    }
  }

  @TypeClass
  public interface ReifiedContainsVoid<E extends Expr<E>> {
    boolean reify();

    static <E extends Expr<E>> boolean containsVoid(ReifiedContainsVoid<E> containsVoid, E ignore) {
      return containsVoid.reify();
    }

    @TypeClass.Witness
    static <E extends Expr<E>, F> ReifiedContainsVoid<E> reifiedHere(
        ContainsVoid<E, F> here, ReifiedFact<F> fact) {
      return fact::reify;
    }
  }
}
