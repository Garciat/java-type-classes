package com.garciat.typeclasses;

import static com.garciat.typeclasses.TyEq.refl;
import static com.garciat.typeclasses.api.TypeClass.Witness.Overlap.OVERLAPPING;
import static com.garciat.typeclasses.impl.Functions.curry;
import static com.garciat.typeclasses.impl.Functions.flip;
import static java.util.function.Function.identity;

import com.garciat.typeclasses.api.Kind;
import com.garciat.typeclasses.api.Kind.KArr;
import com.garciat.typeclasses.api.Kind.KStar;
import com.garciat.typeclasses.api.TApp;
import com.garciat.typeclasses.api.TPar;
import com.garciat.typeclasses.api.TagBase;
import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.impl.Lists;
import com.garciat.typeclasses.impl.Maps;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Core type class infrastructure for Java.
 *
 * <p>This class has been retained for backward compatibility but is no longer the main entry point.
 * The library should be accessed through the public type classes and data types.
 *
 * @deprecated Use {@link Examples} for demonstration code, or the individual type classes directly.
 */
@Deprecated
public final class Main {
  private Main() {}
}

// ==== Type System ====
// Kind, TApp, TPar, TagBase are now in com.garciat.typeclasses.api package

// Internal type parsing
sealed interface ParsedType {
  record Var(TypeVariable<?> java) implements ParsedType {}

  record App(ParsedType fun, ParsedType arg) implements ParsedType {}

  record ArrayOf(ParsedType elementType) implements ParsedType {}

  record Const(Class<?> java) implements ParsedType {}

  record Primitive(Class<?> java) implements ParsedType {}

  default String format() {
    return switch (this) {
      case Var v -> v.java.getName();
      case Const c ->
          c.java().getSimpleName()
              + Arrays.stream(c.java().getTypeParameters())
                  .map(TypeVariable::getName)
                  .reduce((a, b) -> a + ", " + b)
                  .map(s -> "[" + s + "]")
                  .orElse("");
      case App a -> a.fun.format() + "(" + a.arg.format() + ")";
      case ArrayOf a -> a.elementType.format() + "[]";
      case Primitive p -> p.java().getSimpleName();
    };
  }

  static List<ParsedType> parseAll(Type[] types) {
    return Arrays.stream(types).map(ParsedType::parse).toList();
  }

  static ParsedType parse(Type java) {
    return switch (java) {
      case Class<?> tag when parseTagType(tag) instanceof Maybe.Just<Class<?>>(var tagged) ->
          new Const(tagged);
      case Class<?> arr when arr.isArray() -> new ArrayOf(parse(arr.getComponentType()));
      case Class<?> prim when prim.isPrimitive() -> new Primitive(prim);
      case Class<?> c -> new Const(c);
      case TypeVariable<?> v -> new Var(v);
      case ParameterizedType p
          when parseAppType(p)
              instanceof Maybe.Just<Pair<Type, Type>>(Pair<Type, Type>(var fun, var arg)) ->
          new App(parse(fun), parse(arg));
      case ParameterizedType p ->
          parseAll(p.getActualTypeArguments()).stream().reduce(parse(p.getRawType()), App::new);
      case GenericArrayType a -> new ArrayOf(parse(a.getGenericComponentType()));
      case WildcardType w -> throw new IllegalArgumentException("Cannot parse wildcard type: " + w);
      default -> throw new IllegalArgumentException("Unsupported type: " + java);
    };
  }

  private static Maybe<Class<?>> parseTagType(Class<?> c) {
    return switch (c.getEnclosingClass()) {
      case Class<?> enclosing when c.getSuperclass().equals(TagBase.class) -> Maybe.just(enclosing);
      case null -> Maybe.nothing();
      default -> Maybe.nothing();
    };
  }

  private static Maybe<Pair<Type, Type>> parseAppType(ParameterizedType t) {
    return switch (t.getRawType()) {
      case Class<?> raw when raw.equals(TApp.class) || raw.equals(TPar.class) ->
          Maybe.just(Pair.of(t.getActualTypeArguments()[0], t.getActualTypeArguments()[1]));
      default -> Maybe.nothing();
    };
  }
}

// Internal unification algorithm
class Unification {
  public static Maybe<Map<ParsedType.Var, ParsedType>> unify(ParsedType t1, ParsedType t2) {
    return switch (Pair.of(t1, t2)) {
      case Pair<ParsedType, ParsedType>(ParsedType.Var var1, ParsedType.Primitive p) ->
          Maybe.nothing(); // no primitives in generics
      case Pair<ParsedType, ParsedType>(ParsedType.Var var1, var t) -> Maybe.just(Map.of(var1, t));
      case Pair<ParsedType, ParsedType>(ParsedType.Const const1, ParsedType.Const const2)
          when const1.equals(const2) ->
          Maybe.just(Map.of());
      case Pair<ParsedType, ParsedType>(
              ParsedType.App(var fun1, var arg1),
              ParsedType.App(var fun2, var arg2)) ->
          Maybe.apply(Maps::merge, unify(fun1, fun2), unify(arg1, arg2));
      case Pair<ParsedType, ParsedType>(
              ParsedType.ArrayOf(var elem1),
              ParsedType.ArrayOf(var elem2)) ->
          unify(elem1, elem2);
      case Pair<ParsedType, ParsedType>(
              ParsedType.Primitive(var prim1),
              ParsedType.Primitive(var prim2))
          when prim1.equals(prim2) ->
          Maybe.just(Map.of());
      default -> Maybe.nothing();
    };
  }

  public static ParsedType substitute(Map<ParsedType.Var, ParsedType> map, ParsedType type) {
    return switch (type) {
      case ParsedType.Var var -> map.getOrDefault(var, var);
      case ParsedType.App(var fun, var arg) ->
          new ParsedType.App(substitute(map, fun), substitute(map, arg));
      case ParsedType.ArrayOf var -> new ParsedType.ArrayOf(substitute(map, var.elementType()));
      case ParsedType.Primitive p -> p;
      case ParsedType.Const c -> c;
    };
  }

  public static List<ParsedType> substituteAll(
      Map<ParsedType.Var, ParsedType> map, List<ParsedType> types) {
    return types.stream().map(t -> substitute(map, t)).toList();
  }
}

// Internal function type representation
record FuncType(Method java, List<ParsedType> paramTypes, ParsedType returnType) {
  public String format() {
    return String.format(
        "%s%s -> %s",
        Arrays.stream(java.getTypeParameters())
            .map(TypeVariable::getName)
            .reduce((a, b) -> a + " " + b)
            .map("∀ %s. "::formatted)
            .orElse(""),
        paramTypes.stream().map(ParsedType::format).collect(Collectors.joining(", ", "(", ")")),
        returnType.format());
  }

  public static FuncType parse(Method method) {
    if (!Modifier.isStatic(method.getModifiers())) {
      throw new IllegalArgumentException("Method must be static: " + method);
    }
    return new FuncType(
        method,
        ParsedType.parseAll(method.getGenericParameterTypes()),
        ParsedType.parse(method.getGenericReturnType()));
  }
}

// === Type Class System ===
// TypeClass, Ty, Ctx are now in com.garciat.typeclasses.api package

// Internal witness resolution utilities
// TypeClasses is now in com.garciat.typeclasses.TypeClasses

// === First-Order Type Classes ===

@TypeClass
sealed interface TyEq<A, B> {
  A castR(B b);

  B castL(A a);

  static <T> TyEq<T, T> refl() {
    return new Refl<>();
  }

  record Refl<T>() implements TyEq<T, T> {

    @Override
    public T castR(T t) {
      return t;
    }

    @Override
    public T castL(T t) {
      return t;
    }
  }

  @TypeClass.Witness
  static <T> TyEq<T, T> tyEqRefl() {
    return refl();
  }
}

@TypeClass
interface Show<A> {
  String show(A a);

  static <A> String show(Show<A> showA, A a) {
    return showA.show(a);
  }

  @TypeClass.Witness
  static Show<Integer> integerShow() {
    return i -> Integer.toString(i);
  }

  @TypeClass.Witness
  static Show<String> stringShow() {
    return s -> "\"" + s + "\"";
  }

  @TypeClass.Witness
  static <A> Show<Optional<A>> optionalShow(Show<A> showA) {
    return optA -> optA.map(a -> "Some(" + showA.show(a) + ")").orElse("None");
  }

  @TypeClass.Witness
  static <A> Show<A[]> arrayShow(Show<A> showA) {
    return arrayA ->
        Arrays.stream(arrayA).map(showA::show).collect(Collectors.joining(", ", "[", "]"));
  }

  @TypeClass.Witness
  static Show<int[]> intArrayShow() {
    return arrayA ->
        Arrays.stream(arrayA)
            .mapToObj(Integer::toString)
            .collect(Collectors.joining(", ", "[", "]"));
  }

  @TypeClass.Witness
  static <A> Show<List<A>> listShow(Show<A> showA) {
    return listA -> listA.stream().map(showA::show).collect(Collectors.joining(", ", "[", "]"));
  }

  @TypeClass.Witness
  static <K, V> Show<Map<K, V>> mapShow(Show<K> showK, Show<V> showV) {
    return mapKV ->
        mapKV.entrySet().stream()
            .map(entry -> showK.show(entry.getKey()) + ": " + showV.show(entry.getValue()))
            .collect(Collectors.joining(", ", "{", "}"));
  }
}

@TypeClass
interface Eq<A> {
  boolean eq(A a1, A a2);

  static <A> boolean eq(Eq<A> eqA, A a1, A a2) {
    return eqA.eq(a1, a2);
  }

  @TypeClass.Witness
  static Eq<Integer> integerEq() {
    return Integer::equals;
  }

  @TypeClass.Witness
  static Eq<String> stringEq() {
    return String::equals;
  }

  @TypeClass.Witness
  static <A> Eq<Optional<A>> optionalEq(Eq<A> eqA) {
    return (optA1, optA2) ->
        optA1.isPresent() && optA2.isPresent()
            ? eqA.eq(optA1.get(), optA2.get())
            : optA1.isEmpty() && optA2.isEmpty();
  }

  @TypeClass.Witness
  static <A> Eq<List<A>> listEq(Eq<A> eqA) {
    return (listA1, listA2) -> {
      if (listA1.size() != listA2.size()) {
        return false;
      }
      for (int i = 0; i < listA1.size(); i++) {
        if (!eqA.eq(listA1.get(i), listA2.get(i))) {
          return false;
        }
      }
      return true;
    };
  }

  @TypeClass.Witness
  static <K, V> Eq<Map<K, V>> mapEq(Eq<K> eqK, Eq<V> eqV) {
    return (map1, map2) -> {
      if (map1.size() != map2.size()) {
        return false;
      }
      for (Map.Entry<K, V> entry1 : map1.entrySet()) {
        boolean found = false;
        for (Map.Entry<K, V> entry2 : map2.entrySet()) {
          if (eqK.eq(entry1.getKey(), entry2.getKey())
              && eqV.eq(entry1.getValue(), entry2.getValue())) {
            found = true;
            break;
          }
        }
        if (!found) {
          return false;
        }
      }
      return true;
    };
  }
}

enum Ordering {
  LT,
  EQ,
  GT
}

@TypeClass
interface Ord<A> extends Eq<A> {
  Ordering compare(A a1, A a2);

  @Override
  default boolean eq(A a1, A a2) {
    return compare(a1, a2) == Ordering.EQ;
  }

  static <A> Ordering compare(Ord<A> ordA, A a1, A a2) {
    return ordA.compare(a1, a2);
  }

  static <A> boolean lt(Ord<A> ordA, A a1, A a2) {
    return ordA.compare(a1, a2) == Ordering.LT;
  }

  @TypeClass.Witness
  static Ord<Integer> integerOrd() {
    return (a1, a2) -> a1 < a2 ? Ordering.LT : a1 > a2 ? Ordering.GT : Ordering.EQ;
  }

  @TypeClass.Witness
  static <A> Ord<Optional<A>> optionalOrd(Ord<A> ordA) {
    return (optA1, optA2) -> {
      if (optA1.isPresent() && optA2.isPresent()) {
        return ordA.compare(optA1.get(), optA2.get());
      } else if (optA1.isEmpty() && optA2.isEmpty()) {
        return Ordering.EQ;
      } else if (optA1.isEmpty()) {
        return Ordering.LT;
      } else {
        return Ordering.GT;
      }
    };
  }
}

@TypeClass
interface Monoid<A> {
  A combine(A a1, A a2);

  A identity();

  static <A> A combineAll(Monoid<A> monoid, List<A> elements) {
    A result = monoid.identity();
    for (A element : elements) {
      result = monoid.combine(result, element);
    }
    return result;
  }

  static <A> Monoid<A> of(Supplier<A> identity, BinaryOperator<A> combine) {
    return new Monoid<>() {
      @Override
      public A combine(A a1, A a2) {
        return combine.apply(a1, a2);
      }

      @Override
      public A identity() {
        return identity.get();
      }
    };
  }

  @TypeClass.Witness
  static Monoid<String> stringMonoid() {
    return new Monoid<>() {
      @Override
      public String combine(String s1, String s2) {
        return s1 + s2;
      }

      @Override
      public String identity() {
        return "";
      }
    };
  }
}

@TypeClass
interface Num<A> {
  A add(A a1, A a2);

  A mul(A a1, A a2);

  A zero();

  A one();

  @TypeClass.Witness
  static Num<Integer> integerNum() {
    return new Num<>() {
      @Override
      public Integer add(Integer a1, Integer a2) {
        return a1 + a2;
      }

      @Override
      public Integer mul(Integer a1, Integer a2) {
        return a1 * a2;
      }

      @Override
      public Integer zero() {
        return 0;
      }

      @Override
      public Integer one() {
        return 1;
      }
    };
  }
}

@TypeClass
interface RandomGen<G> {
  Pair<Integer, G> next(G gen);

  Pair<G, G> split(G gen);

  @TypeClass.Witness
  static RandomGen<java.util.Random> javaUtilRandomGen() {
    return new RandomGen<>() {
      @Override
      public Pair<Integer, java.util.Random> next(java.util.Random gen) {
        return Pair.of(gen.nextInt(), gen);
      }

      @Override
      public Pair<java.util.Random, java.util.Random> split(java.util.Random gen) {
        java.util.Random gen1 = new java.util.Random(gen.nextLong());
        java.util.Random gen2 = new java.util.Random(gen.nextLong());
        return Pair.of(gen1, gen2);
      }
    };
  }
}

@TypeClass
interface Random<A> {
  <G> Pair<A, G> random(RandomGen<G> randomGen, G gen);

  @TypeClass.Witness
  static Random<Integer> integerRandom() {
    return new Random<>() {
      @Override
      public <G> Pair<Integer, G> random(RandomGen<G> randomGen, G gen) {
        return randomGen.next(gen);
      }
    };
  }
}

@TypeClass
interface Arbitrary<A> {
  Gen<A> arbitrary();

  @TypeClass.Witness
  static Arbitrary<Integer> integerArbitrary() {
    return () -> Gen.chooseInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  @TypeClass.Witness
  static <A> Arbitrary<Optional<A>> optionalArbitrary(Arbitrary<A> arbA) {
    return () -> {
      Gen<A> genA = arbA.arbitrary();
      return (seed, size) -> {
        Gen<Integer> genBool = Gen.chooseInt(0, 2);
        if (genBool.generate(seed, size) == 0) {
          return Optional.of(genA.generate(seed + 1, size));
        } else {
          return Optional.empty();
        }
      };
    };
  }

  @TypeClass.Witness
  static <A> Arbitrary<List<A>> listArbitrary(Arbitrary<A> arbA) {
    return () -> arbA.arbitrary().listOf();
  }

  @TypeClass.Witness
  static <A, B> Arbitrary<Function<A, B>> functionArbitrary(
      CoArbitrary<A> coarb, Arbitrary<B> arbB) {
    return () -> {
      Gen<B> genB = arbB.arbitrary();
      return (seed, size) -> a -> coarb.coarbitrary(a, genB).generate(seed, size);
    };
  }
}

@TypeClass
interface CoArbitrary<A> {
  <B> Gen<B> coarbitrary(A a, Gen<B> genB);

  @TypeClass.Witness
  static CoArbitrary<Integer> integerCoArbitrary() {
    return new CoArbitrary<>() {
      @Override
      public <B> Gen<B> coarbitrary(Integer a, Gen<B> genB) {
        return genB.variant(a);
      }
    };
  }

  @TypeClass.Witness
  static <A> CoArbitrary<Optional<A>> optionalCoArbitrary(CoArbitrary<A> coarbA) {
    return new CoArbitrary<>() {
      @Override
      public <B> Gen<B> coarbitrary(Optional<A> optA, Gen<B> genB) {
        if (optA.isPresent()) {
          return coarbA.coarbitrary(optA.get(), genB).variant(1);
        } else {
          return genB.variant(0);
        }
      }
    };
  }

  @TypeClass.Witness
  static <A> CoArbitrary<List<A>> listCoArbitrary(CoArbitrary<A> coarbA) {
    return new CoArbitrary<>() {
      @Override
      public <B> Gen<B> coarbitrary(List<A> listA, Gen<B> genB) {
        Gen<B> resultGen = genB.variant(listA.size());
        for (A a : listA) {
          resultGen = coarbA.coarbitrary(a, resultGen).variant(1);
        }
        return resultGen;
      }
    };
  }

  @TypeClass.Witness
  static <A, B> CoArbitrary<Function<A, B>> functionCoArbitrary(
      Arbitrary<A> arbA, CoArbitrary<B> coarbB) {
    return new CoArbitrary<>() {
      @Override
      public <C> Gen<C> coarbitrary(Function<A, B> f, Gen<C> genC) {
        return Arbitrary.listArbitrary(arbA)
            .arbitrary()
            .flatMap(xs -> CoArbitrary.listCoArbitrary(coarbB).coarbitrary(Lists.map(xs, f), genC));
      }
    };
  }
}

// === Higher-Kinded Type Classes ===

record Endo<A>(Function<A, A> appEndo) {
  public Endo<A> compose(Endo<A> other) {
    return new Endo<>(a -> appEndo.apply(other.appEndo.apply(a)));
  }

  public static <A> Endo<A> id() {
    return new Endo<>(a -> a);
  }

  public static <A> Endo<A> of(Function<A, A> f) {
    return new Endo<>(f);
  }

  public static <A, B> Function<A, Endo<B>> of(BiFunction<A, B, B> f) {
    return a -> new Endo<>(b -> f.apply(a, b));
  }

  @TypeClass.Witness
  static <A> Monoid<Endo<A>> monoid() {
    return Monoid.of(Endo::id, Endo::compose);
  }
}

record Dual<A>(A getDual) {
  public static <A> Dual<A> of(A a) {
    return new Dual<>(a);
  }

  public static <A> Monoid<Dual<A>> monoid(Monoid<A> monoidA) {
    return Monoid.of(
        () -> new Dual<>(monoidA.identity()),
        (d1, d2) -> new Dual<>(monoidA.combine(d2.getDual, d1.getDual)));
  }
}

// class Foldable t where
@TypeClass
interface Foldable<T extends Kind<KArr<KStar>>> {
  // foldMap :: Monoid m => (a -> m) -> t a -> m
  <A, M> M foldMap(Monoid<M> monoid, Function<A, M> f, TApp<T, A> ta);

  // fold :: Monoid m => t m -> m
  default <A> A fold(TApp<T, A> ta, Monoid<A> monoid) {
    return foldMap(monoid, identity(), ta);
  }

  // foldr :: (a -> b -> b) -> b -> t a -> b
  // foldr f z t = appEndo (foldMap (Endo . f) t) z
  default <A, B> B foldr(BiFunction<A, B, B> f, B z, TApp<T, A> t) {
    Endo<B> endo = foldMap(Endo.monoid(), curry(f).andThen(Endo::of), t);
    return endo.appEndo().apply(z);
  }

  // foldl :: (b -> a -> b) -> b -> t a -> b
  // foldl f z t = appEndo (getDual (foldMap (Dual . Endo . flip f) t)) z
  default <A, B> B foldl(BiFunction<B, A, B> f, B z, TApp<T, A> t) {
    Dual<Endo<B>> dualEndo =
        foldMap(Dual.monoid(Endo.monoid()), curry(flip(f)).andThen(Endo::of).andThen(Dual::of), t);
    return dualEndo.getDual().appEndo().apply(z);
  }

  // toList :: t a -> [a]
  default <A> FwdList<A> toList(TApp<T, A> ta) {
    return FwdList.build(
        new FwdList.Builder<A>() {
          @Override
          public <B> B apply(BiFunction<A, B, B> cons, Supplier<B> nil) {
            return foldr(cons, nil.get(), ta);
          }
        });
  }

  // null :: t a -> Bool
  default <A> boolean isEmpty(TApp<T, A> ta) {
    return foldr((a, b) -> false, true, ta);
  }

  // length :: t a -> Int
  default <A> int length(TApp<T, A> ta) {
    return foldl((n, a) -> n + 1, 0, ta);
  }
}

@TypeClass
interface Traversable<T extends Kind<KArr<KStar>>> extends Functor<T>, Foldable<T> {
  <F extends Kind<KArr<KStar>>, A, B> TApp<F, ? extends TApp<T, B>> traverse(
      Applicative<F> applicative, Function<A, ? extends TApp<F, B>> f, TApp<T, A> ta);

  static <F extends Kind<KArr<KStar>>, T extends Kind<KArr<KStar>>, A, B>
      TApp<F, ? extends TApp<T, B>> traverse(
          Traversable<T> traversable,
          Applicative<F> applicative,
          TApp<T, A> tA,
          Function<A, TApp<F, B>> f) {
    return traversable.traverse(applicative, f, tA);
  }
}

@TypeClass
interface Functor<F extends Kind<KArr<KStar>>> {
  <A, B> TApp<F, B> map(Function<A, B> f, TApp<F, A> fa);
}

@TypeClass
interface Applicative<F extends Kind<KArr<KStar>>> extends Functor<F> {
  <A> TApp<F, A> pure(A a);

  <A, B> TApp<F, B> ap(TApp<F, Function<A, B>> ff, TApp<F, A> fa);

  @Override
  default <A, B> TApp<F, B> map(Function<A, B> f, TApp<F, A> fa) {
    return ap(pure(f), fa);
  }

  default <A, B, C> BiFunction<TApp<F, A>, TApp<F, B>, TApp<F, C>> lift(BiFunction<A, B, C> f) {
    return (fa, fb) -> ap(ap(pure(a -> b -> f.apply(a, b)), fa), fb);
  }

  default <A> TApp<F, FwdList<A>> sequence(FwdList<? extends TApp<F, A>> fas) {
    return fas.traverse(this, identity());
  }

  default <A> TApp<F, JavaList<A>> sequence(JavaList<? extends TApp<F, A>> fas) {
    return fas.traverse(this, identity());
  }
}

@TypeClass
interface Alternative<F extends Kind<KArr<KStar>>> extends Applicative<F> {
  <A> TApp<F, A> empty();

  <A> TApp<F, A> alt(TApp<F, A> fa1, TApp<F, A> fa2);
}

@TypeClass
interface Monad<M extends Kind<KArr<KStar>>> extends Applicative<M> {
  <A, B> TApp<M, B> flatMap(Function<A, TApp<M, B>> f, TApp<M, A> fa);

  @Override
  default <A, B> TApp<M, B> map(Function<A, B> f, TApp<M, A> fa) {
    return flatMap(a -> pure(f.apply(a)), fa);
  }

  @Override
  default <A, B> TApp<M, B> ap(TApp<M, Function<A, B>> ff, TApp<M, A> fa) {
    return flatMap(a -> map(f -> f.apply(a), ff), fa);
  }
}

// === Functional Types ===

record JavaList<A>(List<A> toList) implements TApp<JavaList.Tag, A> {

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

  @TypeClass.Witness
  public static <A> Show<JavaList<A>> show(Show<A> showA) {
    return listA ->
        listA.toList().stream().map(showA::show).collect(Collectors.joining(", ", "[", "]"));
  }

  @TypeClass.Witness
  public static Functor<JavaList.Tag> functor() {
    return new Control();
  }

  @TypeClass.Witness
  public static Traversable<JavaList.Tag> traversable() {
    return new Control();
  }

  private static final class Control
      implements Functor<JavaList.Tag>, Foldable<JavaList.Tag>, Traversable<JavaList.Tag> {
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

  public static <T> JavaList<T> unwrap(TApp<JavaList.Tag, T> x) {
    return (JavaList<T>) x;
  }

  public static final class Tag extends TagBase<KArr<KStar>> {}
}

record Sum<A>(A value) {
  @TypeClass.Witness
  public static <A> Monoid<Sum<A>> monoid(Num<A> num) {
    return new Monoid<>() {
      @Override
      public Sum<A> combine(Sum<A> s1, Sum<A> s2) {
        return new Sum<>(num.add(s1.value(), s2.value()));
      }

      @Override
      public Sum<A> identity() {
        return new Sum<>(num.zero());
      }
    };
  }
}

@FunctionalInterface
interface Gen<A> {
  A generate(long seed, int size);

  default <B> Gen<B> map(Function<A, B> f) {
    return (seed, size) -> f.apply(generate(seed, size));
  }

  // TODO: This is a naive implementation; in a real implementation, the seed
  // management would be
  // more sophisticated.
  default <B> Gen<B> flatMap(Function<A, Gen<B>> f) {
    return (seed, size) -> f.apply(generate(seed, size)).generate(seed + 1, size);
  }

  default Gen<A> variant(int n) {
    return (seed, size) -> generate(seed + n, size);
  }

  default Gen<List<A>> listOf() {
    return sized(size -> chooseInt(0, size).flatMap(this::vectorOf));
  }

  default Gen<List<A>> vectorOf(int length) {
    return (seed, size) -> {
      List<A> result = new ArrayList<>();
      for (int i = 0; i < length; i++) {
        result.add(generate(seed + i, size));
      }
      return result;
    };
  }

  static Gen<Integer> chooseInt(int low, int high) {
    return (seed, size) -> new java.util.Random(seed).nextInt(low, high);
  }

  static <A> Gen<A> sized(Function<Integer, Gen<A>> gen) {
    return (seed, size) -> gen.apply(size).generate(seed, size);
  }
}

record Pair<A, B>(A fst, B snd) {

  public <X> Pair<X, B> mapFst(Function<A, X> f) {
    return Pair.of(f.apply(fst), snd);
  }

  public <Y> Pair<A, Y> mapSnd(Function<B, Y> f) {
    return Pair.of(fst, f.apply(snd));
  }

  public static <A, B> Pair<A, B> of(A first, B second) {
    return new Pair<>(first, second);
  }
}

sealed interface Either<L, R> extends TApp<TPar<Either.Tag, L>, R> {
  record Left<L, R>(L value) implements Either<L, R> {}

  record Right<L, R>(R value) implements Either<L, R> {}

  static <L, R> Either<L, R> left(L value) {
    return new Left<>(value);
  }

  static <L, R> Either<L, R> right(R value) {
    return new Right<>(value);
  }

  default <X> Either<L, X> map(Function<? super R, ? extends X> f) {
    return fold(Either::left, f.andThen(Either::right));
  }

  default <X> Either<X, R> mapLeft(Function<? super L, ? extends X> f) {
    return fold(f.andThen(Either::left), Either::right);
  }

  default <X> Either<L, X> flatMap(Function<? super R, ? extends Either<L, X>> f) {
    return fold(Either::left, f);
  }

  default <A> A fold(
      Function<? super L, ? extends A> fLeft, Function<? super R, ? extends A> fRight) {
    return switch (this) {
      case Left<L, R>(L value) -> fLeft.apply(value);
      case Right<L, R>(R value) -> fRight.apply(value);
    };
  }

  static <A, L, R> Either<L, List<R>> traverse(List<A> list, Function<? super A, Either<L, R>> f) {
    return unwrap(JavaList.of(list).traverse(Either.applicative(), f::apply)).map(JavaList::toList);
  }

  @TypeClass.Witness
  static <L> Functor<TPar<Tag, L>> functor() {
    return new Functor<>() {
      @Override
      public <A, B> TApp<TPar<Tag, L>, B> map(Function<A, B> f, TApp<TPar<Tag, L>, A> fa) {
        return unwrap(fa).map(f);
      }
    };
  }

  @TypeClass.Witness
  static <L> Applicative<TPar<Tag, L>> applicative() {
    return monad();
  }

  @TypeClass.Witness
  static <L> Monad<TPar<Tag, L>> monad() {
    return new Monad<>() {
      @Override
      public <A> TApp<TPar<Tag, L>, A> pure(A a) {
        return right(a);
      }

      @Override
      public <A, B> TApp<TPar<Tag, L>, B> flatMap(
          Function<A, TApp<TPar<Tag, L>, B>> f, TApp<TPar<Tag, L>, A> fa) {
        return unwrap(fa).flatMap(a -> unwrap(f.apply(a)));
      }
    };
  }

  final class Tag extends TagBase<KArr<KArr<KStar>>> {}

  static <L, R> Either<L, R> unwrap(TApp<TPar<Tag, L>, R> value) {
    return (Either<L, R>) value;
  }
}

sealed interface Maybe<A> extends TApp<Maybe.Tag, A> {
  record Just<A>(A value) implements Maybe<A> {}

  record Nothing<A>() implements Maybe<A> {}

  static <A> Maybe<A> just(A value) {
    return new Just<>(value);
  }

  static <A> Maybe<A> nothing() {
    return new Nothing<>();
  }

  default <R> R fold(Supplier<R> onNothing, Function<A, R> onJust) {
    return switch (this) {
      case Just<A>(A value) -> onJust.apply(value);
      case Nothing<A>() -> onNothing.get();
    };
  }

  default Maybe<A> filter(Function<A, Boolean> predicate) {
    return flatMap(a -> predicate.apply(a) ? just(a) : nothing());
  }

  default Stream<A> stream() {
    return fold(Stream::empty, Stream::of);
  }

  default <B> Maybe<B> map(Function<A, B> f) {
    return fold(Maybe::nothing, a -> just(f.apply(a)));
  }

  default <B> Maybe<B> flatMap(Function<A, Maybe<B>> f) {
    return switch (this) {
      case Just<A>(A value) -> f.apply(value);
      case Nothing<A>() -> nothing();
    };
  }

  static <A, B, C> BiFunction<Maybe<A>, Maybe<B>, Maybe<C>> lift(BiFunction<A, B, C> f) {
    return (ma, mb) -> ma.flatMap(a -> mb.map(b -> f.apply(a, b)));
  }

  static <A, B, C> Maybe<C> apply(BiFunction<A, B, C> f, Maybe<A> ma, Maybe<B> mb) {
    return lift(f).apply(ma, mb);
  }

  @TypeClass.Witness
  static Functor<Tag> functor() {
    return new Functor<>() {
      @Override
      public <A, B> TApp<Tag, B> map(Function<A, B> f, TApp<Tag, A> fa) {
        return unwrap(fa).map(f);
      }
    };
  }

  @TypeClass.Witness
  static Applicative<Tag> applicative() {
    return new Applicative<>() {
      @Override
      public <A> TApp<Tag, A> pure(A a) {
        return just(a);
      }

      @Override
      public <A, B> TApp<Tag, B> ap(TApp<Tag, Function<A, B>> ff, TApp<Tag, A> fa) {
        return unwrap(ff).flatMap(f -> unwrap(fa).map(f));
      }
    };
  }

  @TypeClass.Witness
  static Monad<Tag> monad() {
    return new Monad<>() {
      @Override
      public <A> TApp<Tag, A> pure(A a) {
        return just(a);
      }

      @Override
      public <A, B> TApp<Tag, B> flatMap(Function<A, TApp<Tag, B>> f, TApp<Tag, A> fa) {
        return unwrap(fa).flatMap(a -> unwrap(f.apply(a)));
      }
    };
  }

  final class Tag extends TagBase<KArr<KStar>> {}

  static <A> Maybe<A> unwrap(TApp<Tag, A> value) {
    return (Maybe<A>) value;
  }
}

@FunctionalInterface
interface State<S, A> extends TApp<TPar<State.Tag, S>, A> {
  Pair<A, S> run(S state);

  static <S, A> State<S, A> of(Function<S, Pair<A, S>> f) {
    return f::apply;
  }

  static <S, A> State<S, A> pure(A a) {
    return state -> Pair.of(a, state);
  }

  default <B> State<S, B> map(Function<A, B> f) {
    return state -> run(state).mapFst(f);
  }

  default <B> State<S, B> flatMap(Function<A, State<S, B>> f) {
    return state ->
        switch (run(state)) {
          case Pair<A, S>(A a, S newState) -> f.apply(a).run(newState);
        };
  }

  @TypeClass.Witness
  static <S> Functor<TPar<Tag, S>> functor() {
    return new Functor<>() {
      @Override
      public <A, B> TApp<TPar<Tag, S>, B> map(Function<A, B> f, TApp<TPar<Tag, S>, A> fa) {
        return unwrap(fa).map(f);
      }
    };
  }

  @TypeClass.Witness
  static <S> Applicative<TPar<Tag, S>> applicative() {
    return new Applicative<>() {
      @Override
      public <A> TApp<TPar<Tag, S>, A> pure(A a) {
        return State.pure(a);
      }

      @Override
      public <A, B> TApp<TPar<Tag, S>, B> ap(
          TApp<TPar<Tag, S>, Function<A, B>> ff, TApp<TPar<Tag, S>, A> fa) {
        return unwrap(ff).flatMap(f -> unwrap(fa).map(f));
      }
    };
  }

  @TypeClass.Witness
  static <S> Monad<TPar<Tag, S>> monad() {
    return new Monad<>() {
      @Override
      public <A> TApp<TPar<Tag, S>, A> pure(A a) {
        return State.pure(a);
      }

      @Override
      public <A, B> TApp<TPar<Tag, S>, B> flatMap(
          Function<A, TApp<TPar<Tag, S>, B>> f, TApp<TPar<Tag, S>, A> fa) {
        return unwrap(fa).flatMap(a -> unwrap(f.apply(a)));
      }
    };
  }

  final class Tag extends TagBase<KArr<KArr<KStar>>> {}

  static <S, A> State<S, A> unwrap(TApp<TPar<Tag, S>, A> value) {
    return (State<S, A>) value;
  }
}

sealed interface FwdList<A> extends TApp<FwdList.Tag, A> {
  record Nil<A>() implements FwdList<A> {}

  record Cons<A>(A head, FwdList<A> tail) implements FwdList<A> {}

  default <R> R match(Supplier<R> onNil, BiFunction<A, FwdList<A>, R> onCons) {
    return switch (this) {
      case Nil<A>() -> onNil.get();
      case Cons<A>(A head, FwdList<A> tail) -> onCons.apply(head, tail);
    };
  }

  default <M> M foldMap(Monoid<M> monoid, Function<A, M> f) {
    return match(
        monoid::identity, (head, tail) -> monoid.combine(f.apply(head), tail.foldMap(monoid, f)));
  }

  default <B> B foldr(B identity, BiFunction<A, B, B> f) {
    return match(() -> identity, (head, tail) -> f.apply(head, tail.foldr(identity, f)));
  }

  default <B> B foldl(B identity, BiFunction<B, A, B> f) {
    return match(() -> identity, (head, tail) -> tail.foldl(f.apply(identity, head), f));
  }

  default void forEach(Consumer<A> action) {
    this.<Void>match(
        () -> null,
        (head, tail) -> {
          action.accept(head);
          tail.forEach(action);
          return null;
        });
  }

  default <B> FwdList<B> map(Function<A, B> f) {
    return match(FwdList::of, (head, tail) -> cons(f.apply(head), tail.map(f)));
  }

  default <B> FwdList<B> flatMap(Function<A, FwdList<B>> f) {
    return match(FwdList::of, (head, tail) -> append(f.apply(head), tail.flatMap(f)));
  }

  default <T extends Kind<KArr<KStar>>, B> TApp<T, FwdList<B>> traverse(
      Applicative<T> applicative, Function<A, ? extends TApp<T, B>> f) {
    return foldr(
        applicative.pure(FwdList.of()),
        (head, tailT) ->
            applicative.lift((B h, FwdList<B> t) -> cons(h, t)).apply(f.apply(head), tailT));
  }

  static <A> FwdList<A> append(FwdList<A> list1, FwdList<A> list2) {
    return list1.match(() -> list2, (head, tail) -> cons(head, append(tail, list2)));
  }

  static <A> FwdList<A> of() {
    return new Nil<>();
  }

  static <A> FwdList<A> cons(A head, FwdList<A> tail) {
    return new Cons<>(head, tail);
  }

  static <A> FwdList<A> of(A a) {
    return cons(a, of());
  }

  @SafeVarargs
  static <A> FwdList<A> of(A... items) {
    return of(Arrays.asList(items));
  }

  static <A> FwdList<A> of(Iterable<A> iter) {
    return unfoldr(
        iter.iterator(), it -> it.hasNext() ? Maybe.just(Pair.of(it.next(), it)) : Maybe.nothing());
  }

  static FwdList<Character> of(CharSequence str) {
    return unfoldr(
        0,
        index ->
            index < str.length()
                ? Maybe.just(Pair.of(str.charAt(index), index + 1))
                : Maybe.nothing());
  }

  default String toStr(TyEq<A, Character> ty) {
    StringBuilder sb = new StringBuilder();
    forEach(ch -> sb.append((char) ty.castL(ch)));
    return sb.toString();
  }

  static <A, B> FwdList<A> unfoldr(B seed, Function<B, Maybe<Pair<A, B>>> f) {
    Maybe<Pair<A, B>> result = f.apply(seed);
    return result.fold(FwdList::of, pair -> cons(pair.fst(), unfoldr(pair.snd(), f)));
  }

  public static <A> FwdList<A> build(Builder<A> builder) {
    return builder.apply(FwdList::cons, FwdList::of);
  }

  public interface Builder<A> {
    <B> B apply(BiFunction<A, B, B> cons, Supplier<B> nil);
  }

  @TypeClass.Witness
  static <A> Show<FwdList<A>> show(Show<A> showA) {
    return list -> {
      StringBuilder sb = new StringBuilder();
      sb.append("[");
      list.forEach(a -> sb.append(showA.show(a)));
      sb.append("]");
      return sb.toString();
    };
  }

  @TypeClass.Witness(overlap = OVERLAPPING)
  static Show<FwdList<Character>> show() {
    return list -> {
      StringBuilder sb = new StringBuilder();
      sb.append("\"");
      list.forEach(sb::append);
      sb.append("\"");
      return sb.toString();
    };
  }

  @TypeClass.Witness
  static Functor<Tag> functor() {
    return new Control();
  }

  @TypeClass.Witness
  static Foldable<Tag> foldable() {
    return new Control();
  }

  @TypeClass.Witness
  static Traversable<Tag> traversable() {
    return new Control();
  }

  @TypeClass.Witness
  static Applicative<Tag> applicative() {
    return new Control();
  }

  @TypeClass.Witness
  static Monad<Tag> monad() {
    return new Control();
  }

  final class Control implements Functor<Tag>, Applicative<Tag>, Monad<Tag>, Traversable<Tag> {
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

    @Override
    public <A> TApp<Tag, A> pure(A a) {
      return FwdList.of(a);
    }

    @Override
    public <A, B> TApp<Tag, B> flatMap(Function<A, TApp<Tag, B>> f, TApp<Tag, A> fa) {
      return unwrap(fa).flatMap(a -> unwrap(f.apply(a)));
    }
  }

  final class Tag extends TagBase<KArr<KStar>> {}

  static <A> FwdList<A> unwrap(TApp<Tag, A> value) {
    return (FwdList<A>) value;
  }
}

@FunctionalInterface
interface Parser<A> extends TApp<Parser.Tag, A> {
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

  @TypeClass.Witness
  static Functor<Parser.Tag> functor() {
    return Control.INSTANCE;
  }

  @TypeClass.Witness
  static Applicative<Parser.Tag> applicative() {
    return Control.INSTANCE;
  }

  @TypeClass.Witness
  static Alternative<Parser.Tag> alternative() {
    return Control.INSTANCE;
  }

  @TypeClass.Witness
  static Monad<Parser.Tag> monad() {
    return Control.INSTANCE;
  }

  final class Control implements Monad<Parser.Tag>, Alternative<Parser.Tag> {
    private static final Control INSTANCE = new Control();

    @Override
    public <A> TApp<Parser.Tag, A> pure(A a) {
      return Parser.pure(a);
    }

    @Override
    public <A, B> TApp<Tag, B> ap(TApp<Tag, Function<A, B>> ff, TApp<Tag, A> fa) {
      return unwrap(fa).applyTo(unwrap(ff));
    }

    @Override
    public <A, B> TApp<Parser.Tag, B> flatMap(
        Function<A, TApp<Parser.Tag, B>> f, TApp<Parser.Tag, A> fa) {
      return unwrap(fa).flatMap(a -> unwrap(f.apply(a)));
    }

    @Override
    public <A> TApp<Parser.Tag, A> empty() {
      return Parser.fail();
    }

    @Override
    public <A> TApp<Parser.Tag, A> alt(TApp<Parser.Tag, A> fa1, TApp<Parser.Tag, A> fa2) {
      return unwrap(fa1).or(unwrap(fa2));
    }
  }

  final class Tag extends TagBase<KArr<KStar>> {}

  static <A> Parser<A> unwrap(TApp<Tag, A> value) {
    return (Parser<A>) value;
  }
}

// === Example Type Class Implementations ===
// These are example implementations demonstrating advanced type class features.
// They are package-private as they're primarily for demonstration purposes.

/**
 * Example type class demonstrating variadic functions through type class resolution.
 *
 * @param <A> the result type
 */
@TypeClass
interface SumAllInt<A> {
  A sum(List<Integer> list);

  static <T> T of(SumAllInt<T> sumAllInt) {
    return sumAllInt.sum(List.of());
  }

  @TypeClass.Witness
  static SumAllInt<Integer> base() {
    return list -> list.stream().mapToInt(Integer::intValue).sum();
  }

  @TypeClass.Witness
  static <A, R> SumAllInt<Function<A, R>> func(SumAllInt<R> sumR, TyEq<A, Integer> eq) {
    return list -> a -> sumR.sum(Lists.concat(list, List.of(eq.castL(a))));
  }

  @TypeClass.Witness
  static <A, R> SumAllInt<F1<A, R>> func1(SumAllInt<Function<A, R>> sumR) {
    return list -> F1.of(sumR.sum(list));
  }

  @TypeClass.Witness
  static <A, B, R> SumAllInt<F2<A, B, R>> func2(SumAllInt<Function<A, Function<B, R>>> sumR) {
    return list -> F2.of(sumR.sum(list));
  }

  @TypeClass.Witness
  static <A, B, C, R> SumAllInt<F3<A, B, C, R>> func3(
      SumAllInt<Function<A, Function<B, Function<C, R>>>> sumR) {
    return list -> F3.of(sumR.sum(list));
  }
}

/**
 * Example type class for variadic printing.
 *
 * @param <T> the result type
 * @see <a href="https://wiki.haskell.org/Varargs">Source</a>
 */
@TypeClass
interface PrintAll<T> {
  T printAll(List<String> strings);

  static <T> T of(PrintAll<T> printAll) {
    return printAll.printAll(List.of());
  }

  @TypeClass.Witness
  static PrintAll<Void> base() {
    return strings -> {
      for (String s : strings) {
        System.out.println(s);
      }
      return null;
    };
  }

  @TypeClass.Witness
  static <A, R> PrintAll<Function<A, R>> func(Show<A> showA, PrintAll<R> printR) {
    return strings -> a -> printR.printAll(Lists.concat(strings, List.of(showA.show(a))));
  }

  @TypeClass
  static <A, R> PrintAll<F1<A, R>> func1(PrintAll<Function<A, R>> printR) {
    return strings -> F1.of(printR.printAll(strings));
  }

  @TypeClass.Witness
  static <A, B, R> PrintAll<F2<A, B, R>> func2(PrintAll<Function<A, Function<B, R>>> printR) {
    return strings -> F2.of(printR.printAll(strings));
  }

  @TypeClass.Witness
  static <A, B, C, R> PrintAll<F3<A, B, C, R>> func3(
      PrintAll<Function<A, Function<B, Function<C, R>>>> printR) {
    return strings -> F3.of(printR.printAll(strings));
  }
}

/** Helper interface for one-argument functions used in examples. */
@FunctionalInterface
interface F1<A, R> {
  R apply(A a);

  static <A, R> F1<A, R> of(Function<A, R> f) {
    return f::apply;
  }
}

/** Helper interface for two-argument functions used in examples. */
@FunctionalInterface
interface F2<A, B, R> {
  R apply(A a, B b);

  static <A, B, R> F2<A, B, R> of(Function<A, Function<B, R>> f) {
    return (a, b) -> f.apply(a).apply(b);
  }
}

/** Helper interface for three-argument functions used in examples. */
@FunctionalInterface
interface F3<A, B, C, R> {
  R apply(A a, B b, C c);

  static <A, B, C, R> F3<A, B, C, R> of(Function<A, Function<B, Function<C, R>>> f) {
    return (a, b, c) -> f.apply(a).apply(b).apply(c);
  }
}

// === Utilities ===
// ZeroOneMore, Lists, Maps, Functions are now in com.garciat.typeclasses.impl package
