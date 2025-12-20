package com.garciat.typeclasses.types;

import com.garciat.typeclasses.api.TypeClass.Witness;
import com.garciat.typeclasses.api.hkt.Kind;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.classes.Applicative;
import com.garciat.typeclasses.classes.Functor;
import com.garciat.typeclasses.classes.Monoid;
import com.garciat.typeclasses.classes.Show;
import com.garciat.typeclasses.classes.Traversable;
import com.garciat.typeclasses.utils.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public record JavaList<A>(List<A> toList) implements TApp<JavaList.Tag, A> {
  public <B> JavaList<B> map(Function<A, B> f) {
    return new JavaList<>(toList().stream().map(f).toList());
  }

  public <B> JavaList<B> flatMap(Function<A, JavaList<B>> f) {
    List<B> result = new ArrayList<>();
    for (A item : toList()) {
      result.addAll(f.apply(item).toList());
    }
    return new JavaList<>(result);
  }

  public <M> M foldMap(Monoid<M> monoid, Function<A, M> f) {
    return toList().stream().map(f).reduce(monoid.identity(), monoid::combine);
  }

  public <F extends Kind<KArr<KStar>>, B> TApp<F, JavaList<B>> traverse(
      Applicative<F> applicative, Function<A, ? extends TApp<F, B>> f) {
    TApp<F, JavaList<B>> result = applicative.pure(JavaList.of());
    for (A item : toList()) {
      TApp<F, B> fb = f.apply(item);
      result =
          applicative
              .lift((JavaList<B> bs, B b) -> JavaList.of(Lists.concat(bs.toList(), List.of(b))))
              .apply(result, fb);
    }
    return result;
  }

  public static <T> JavaList<T> of() {
    return new JavaList<>(List.of());
  }

  @SafeVarargs
  public static <T> JavaList<T> of(T... items) {
    return new JavaList<>(List.of(items));
  }

  public static <T> JavaList<T> of(List<T> list) {
    return new JavaList<>(list);
  }

  @Witness
  public static <A> Show<JavaList<A>> show(Show<A> showA) {
    return listA ->
        listA.toList().stream().map(showA::show).collect(Collectors.joining(", ", "[", "]"));
  }

  @Witness
  public static Functor<Tag> functor() {
    return new Control();
  }

  @Witness
  public static Traversable<Tag> traversable() {
    return new Control();
  }

  private static final class Control implements Traversable<Tag> {
    @Override
    public <A, B> TApp<Tag, B> map(Function<A, B> f, TApp<Tag, A> fa) {
      return unwrap(fa).map(f);
    }

    @Override
    public <A, M> M foldMap(Monoid<M> monoid, Function<A, M> f, TApp<Tag, A> ta) {
      return unwrap(ta).foldMap(monoid, f);
    }

    @Override
    public <F extends Kind<KArr<KStar>>, A, B> TApp<F, ? extends TApp<Tag, B>> traverse(
        Applicative<F> applicative, Function<A, ? extends TApp<F, B>> f, TApp<Tag, A> ta) {
      return unwrap(ta).traverse(applicative, f);
    }
  }

  public static <T> JavaList<T> unwrap(TApp<Tag, T> x) {
    return (JavaList<T>) x;
  }

  public static final class Tag extends TagBase<KArr<KStar>> {}
}
