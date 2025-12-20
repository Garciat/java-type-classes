package com.garciat.typeclasses.types;

import static com.garciat.typeclasses.classes.TyEq.refl;

import com.garciat.typeclasses.api.TypeClass.Witness;
import com.garciat.typeclasses.api.hkt.TApp;
import com.garciat.typeclasses.api.hkt.TagBase;
import com.garciat.typeclasses.classes.Alternative;
import com.garciat.typeclasses.classes.Applicative;
import com.garciat.typeclasses.classes.Functor;
import com.garciat.typeclasses.classes.Monad;
import java.util.function.Function;
import java.util.function.Predicate;

@FunctionalInterface
public interface Parser<A> extends TApp<Parser.Tag, A> {
  Maybe<Pair<A, FwdList<Character>>> parse(FwdList<Character> input);

  default <B> Parser<B> map(Function<A, B> f) {
    return input -> parse(input).map(pair -> pair.mapFst(f));
  }

  default <B> Parser<B> flatMap(Function<A, Parser<B>> f) {
    return input -> parse(input).flatMap(pair -> f.apply(pair.fst()).parse(pair.snd()));
  }

  default Parser<A> or(Parser<A> other) {
    return input -> parse(input).fold(() -> other.parse(input), Maybe::just);
  }

  default <B> Parser<B> applyTo(Parser<Function<A, B>> pf) {
    return pf.flatMap(this::map);
  }

  static <A> Parser<A> pure(A a) {
    return input -> Maybe.just(Pair.of(a, input));
  }

  static <A> Parser<A> fail() {
    return input -> Maybe.nothing();
  }

  static Parser<Character> satisfy(Predicate<Character> predicate) {
    return input ->
        input.match(
            () -> Maybe.nothing(),
            (head, tail) ->
                predicate.test(head) ? Maybe.just(Pair.of(head, tail)) : Maybe.nothing());
  }

  static Parser<Character> charParser(char c) {
    return satisfy(ch -> ch == c);
  }

  static Parser<String> stringParser(String str) {
    return unwrap(FwdList.of(str).traverse(applicative(), Parser::charParser))
        .map(cs -> cs.toStr(refl()));
  }

  @Witness
  static Functor<Tag> functor() {
    return Control.INSTANCE;
  }

  @Witness
  static Applicative<Tag> applicative() {
    return Control.INSTANCE;
  }

  @Witness
  static Alternative<Tag> alternative() {
    return Control.INSTANCE;
  }

  @Witness
  static Monad<Tag> monad() {
    return Control.INSTANCE;
  }

  final class Control implements Monad<Tag>, Alternative<Tag> {
    private static final Control INSTANCE = new Control();

    @Override
    public <A> TApp<Tag, A> pure(A a) {
      return Parser.pure(a);
    }

    @Override
    public <A, B> TApp<Tag, B> ap(TApp<Tag, Function<A, B>> ff, TApp<Tag, A> fa) {
      return unwrap(fa).applyTo(unwrap(ff));
    }

    @Override
    public <A, B> TApp<Tag, B> flatMap(Function<A, TApp<Tag, B>> f, TApp<Tag, A> fa) {
      return unwrap(fa).flatMap(a -> unwrap(f.apply(a)));
    }

    @Override
    public <A> TApp<Tag, A> empty() {
      return Parser.fail();
    }

    @Override
    public <A> TApp<Tag, A> alt(TApp<Tag, A> fa1, TApp<Tag, A> fa2) {
      return unwrap(fa1).or(unwrap(fa2));
    }
  }

  final class Tag extends TagBase<KArr<KStar>> {}

  static <A> Parser<A> unwrap(TApp<Tag, A> value) {
    return (Parser<A>) value;
  }
}
