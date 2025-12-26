package com.garciat.typeclasses.examples;

import static com.garciat.typeclasses.TypeClasses.witness;
import static org.assertj.core.api.Assertions.assertThat;

import com.garciat.typeclasses.api.Lazy;
import com.garciat.typeclasses.api.Out;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.examples.Example3.TyRep.K1;
import com.garciat.typeclasses.examples.Example3.TyRep.Prod;
import com.garciat.typeclasses.examples.Example3.TyRep.Sum;
import com.garciat.typeclasses.examples.Example3.TyRep.Sum.L1;
import com.garciat.typeclasses.examples.Example3.TyRep.Sum.R1;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class Example3 {
  @Test
  void example() {
    Tree.Node<Integer> tree =
        new Tree.Node<>(
            new Tree.Leaf<>(1), new Tree.Node<>(new Tree.Leaf<>(2), new Tree.Leaf<>(3)));

    ToJson<Tree<Integer>> toJsonTree = witness(new Ty<>() {});

    assertThat(toJsonTree.toJson(tree)).isEqualTo(array(value(1), array(value(2), value(3))));
  }

  private static JsonValue array(JsonValue... values) {
    return new JsonValue.JsonArray(List.of(values));
  }

  private static JsonValue value(int value) {
    return new JsonValue.JsonInteger(value);
  }

  @TypeClass
  public interface Generic<A, @Out Rep> {
    Rep from(A a);

    A to(Rep rep);
  }

  public interface TyRep {
    record K1<A>(A value) {}

    sealed interface Sum<A, B> {
      record L1<A, B>(A left) implements Sum<A, B> {}

      record R1<A, B>(B right) implements Sum<A, B> {}
    }

    record Prod<A, B>(A first, B second) {}
  }

  public interface JsonValue {
    record JsonString(String value) implements JsonValue {}

    record JsonInteger(int value) implements JsonValue {}

    record JsonObject(List<Prop> props) implements JsonValue {}

    record JsonArray(List<JsonValue> values) implements JsonValue {}

    record Prop(String key, JsonValue value) {}
  }

  @TypeClass
  public interface ToJson<A> {
    JsonValue toJson(A a);

    @TypeClass.Witness
    static ToJson<Integer> toJsonInteger() {
      return JsonValue.JsonInteger::new;
    }
  }

  @TypeClass
  public interface ToJsonGeneric<Rep> {
    JsonValue toJson(Rep rep);

    static <A, Rep> ToJsonGeneric<Rep> toJsonGeneric(Generic<A, Rep> generic, ToJson<A> toJsonA) {
      return rep -> toJsonA.toJson(generic.to(rep));
    }

    @TypeClass.Witness
    static <A> ToJsonGeneric<K1<A>> k1(Lazy<ToJson<A>> toJsonA) {
      return rep -> toJsonA.get().toJson(rep.value());
    }

    @TypeClass.Witness
    static <A, B> ToJsonGeneric<Prod<A, B>> prod(
        ToJsonGeneric<A> toJsonA, ToJsonGeneric<B> toJsonB) {
      return rep ->
          new JsonValue.JsonArray(
              List.of(toJsonA.toJson(rep.first()), toJsonB.toJson(rep.second())));
    }

    @TypeClass.Witness
    static <A, B> ToJsonGeneric<Sum<A, B>> sum(ToJsonGeneric<A> toJsonA, ToJsonGeneric<B> toJsonB) {
      return rep ->
          switch (rep) {
            case L1(var value) -> toJsonA.toJson(value);
            case R1(var value) -> toJsonB.toJson(value);
          };
    }
  }

  public sealed interface Tree<A> {
    record Leaf<A>(A value) implements Tree<A> {}

    record Node<A>(Tree<A> left, Tree<A> right) implements Tree<A> {}

    @TypeClass.Witness
    static <A, Rep> ToJson<Tree<A>> toJson(
        Generic<Tree<A>, Rep> generic, ToJsonGeneric<Rep> toJsonGeneric) {
      return tree -> toJsonGeneric.toJson(generic.from(tree));
    }

    @TypeClass.Witness
    static <A> Generic<Tree<A>, Sum<K1<A>, Prod<K1<Tree<A>>, K1<Tree<A>>>>> generic() {
      return new Generic<>() {
        @Override
        public Sum<K1<A>, Prod<K1<Tree<A>>, K1<Tree<A>>>> from(Tree<A> tree) {
          return switch (tree) {
            case Leaf<A> leaf ->
                new L1<K1<A>, Prod<K1<Tree<A>>, K1<Tree<A>>>>(new K1<>(leaf.value));
            case Node<A> node ->
                new R1<K1<A>, Prod<K1<Tree<A>>, K1<Tree<A>>>>(
                    new Prod<>(new K1<>(node.left), new K1<>(node.right)));
          };
        }

        @Override
        public Tree<A> to(Sum<K1<A>, Prod<K1<Tree<A>>, K1<Tree<A>>>> rep) {
          return switch (rep) {
            case L1(K1(var value)) -> new Leaf<>(value);
            case R1(Prod(K1(var left), K1(var right))) -> new Node<>(left, right);
          };
        }
      };
    }
  }
}
