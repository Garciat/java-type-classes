package com.garciat.typeclasses;

import static com.garciat.typeclasses.api.TypeClass.Witness.Overlap.OVERLAPPABLE;
import static com.garciat.typeclasses.api.TypeClass.Witness.Overlap.OVERLAPPING;
import static java.lang.reflect.AccessFlag.PUBLIC;
import static java.lang.reflect.AccessFlag.STATIC;

import com.garciat.typeclasses.api.Ctx;
import com.garciat.typeclasses.api.Ty;
import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.impl.FuncType;
import com.garciat.typeclasses.impl.ParsedType;
import com.garciat.typeclasses.impl.Unification;
import com.garciat.typeclasses.impl.utils.Lists;
import com.garciat.typeclasses.impl.utils.ZeroOneMore;
import com.garciat.typeclasses.types.Either;
import com.garciat.typeclasses.types.Maybe;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeClasses {
  public static <T> T witness(Ty<T> ty, Ctx<?>... context) {
    return switch (summon(ParsedType.parse(ty.type()), parseContext(context))) {
      case Either.Left<SummonError, Object>(SummonError error) ->
          throw new WitnessResolutionException(error);
      case Either.Right<SummonError, Object>(Object instance) -> {
        @SuppressWarnings("unchecked")
        T typedInstance = (T) instance;
        yield typedInstance;
      }
    };
  }

  private static List<ContextInstance> parseContext(Ctx<?>[] context) {
    return Arrays.stream(context)
        .map(ctx -> new ContextInstance(ctx.instance(), ParsedType.parse(ctx.type())))
        .toList();
  }

  public static class WitnessResolutionException extends RuntimeException {
    private WitnessResolutionException(SummonError error) {
      super(error.format());
    }
  }

  private sealed interface ResolutionError {
    record NotFound(ParsedType target) implements ResolutionError {}

    record Ambiguous(ParsedType target, List<WitnessRule> candidates) implements ResolutionError {}

    record Nested(ParsedType target, ResolutionError cause) implements ResolutionError {}

    default String format() {
      return switch (this) {
        case NotFound(ParsedType target) -> "No witness found for type: " + target.format();
        case Ambiguous(ParsedType target, List<WitnessRule> candidates) ->
            "Ambiguous witnesses found for type: "
                + target.format()
                + "\nCandidates:\n"
                + candidates.stream()
                    .map(WitnessRule::toString)
                    .collect(Collectors.joining("\n"))
                    .indent(2);
        case Nested(ParsedType target, ResolutionError cause) ->
            "While resolving witness for type: "
                + target.format()
                + "\nCaused by: "
                + cause.format().indent(2);
      };
    }
  }

  private sealed interface InstantiationError {
    record LookupMiss(ParsedType type) implements InstantiationError {}

    record InvocationException(Method method, Exception cause) implements InstantiationError {}

    default String format() {
      return switch (this) {
        case LookupMiss(ParsedType type) -> "Context lookup failed for type: " + type.format();
        case InvocationException(Method method, Exception cause) ->
            "Failed to invoke constructor: " + method + "\nCause: " + cause.getMessage();
      };
    }
  }

  private sealed interface SummonError {
    record Resolution(ResolutionError error) implements SummonError {}

    record Instantiation(InstantiationError error) implements SummonError {}

    default String format() {
      return switch (this) {
        case Resolution(ResolutionError error) -> error.format();
        case Instantiation(InstantiationError error) -> error.format();
      };
    }
  }

  /**
   * Summons a witness for the given target type using the staged resolution approach: parseType >>
   * resolveWitness >> compile >> interpret
   */
  private static Either<SummonError, Object> summon(
      ParsedType target, List<ContextInstance> context) {
    Either<SummonError, InstantiationPlan> resolutionResult =
        resolveWitness(target, context).mapLeft(SummonError.Resolution::new);

    Either<SummonError, Expr<ParsedType>> compilationResult =
        resolutionResult.map(TypeClasses::compile);

    return compilationResult.flatMap(
        expr ->
            interpret(new InterpretContext(context), expr).mapLeft(SummonError.Instantiation::new));
  }

  // ========== Staged Resolution Components ==========

  /**
   * Represents the fully resolved instantiation plan. This is a tree structure where each node is a
   * step in the instantiation process, with dependencies on other steps.
   */
  private sealed interface InstantiationPlan {
    record PlanStep(WitnessRule target, List<InstantiationPlan> dependencies)
        implements InstantiationPlan {}
  }

  /**
   * Represents a reduced AST of interpretable Java operations. This is the compiled form of an
   * InstantiationPlan.
   */
  private sealed interface Expr<K> {
    record InvokeConstructor<K>(Method method, List<Expr<K>> arguments) implements Expr<K> {}

    record Lookup<K>(K key) implements Expr<K> {}
  }

  /** Resolves a ParsedType into an InstantiationPlan. */
  private static Either<ResolutionError, InstantiationPlan> resolveWitness(
      ParsedType target, List<ContextInstance> context) {
    record Match(WitnessRule rule, List<ParsedType> requirements) {}

    List<Match> matches =
        Stream.<WitnessRule>concat(context.stream(), reduceOverlapping(findRules(target)).stream())
            .flatMap(
                rule ->
                    rule
                        .tryMatch(target)
                        .map(requirements -> new Match(rule, requirements))
                        .stream())
            .toList();

    return switch (ZeroOneMore.of(matches)) {
      case ZeroOneMore.One<Match>(Match(var rule, var requirements)) ->
          resolveWitnessAll(requirements, context)
              .<InstantiationPlan>map(
                  dependencies -> new InstantiationPlan.PlanStep(rule, dependencies))
              .mapLeft(error -> new ResolutionError.Nested(target, error));
      case ZeroOneMore.Zero<Match>() -> Either.left(new ResolutionError.NotFound(target));
      case ZeroOneMore.More<Match>(var matches2) ->
          Either.left(
              new ResolutionError.Ambiguous(target, matches2.stream().map(Match::rule).toList()));
    };
  }

  /** Resolves multiple ParsedTypes into a list of InstantiationPlans. */
  private static Either<ResolutionError, List<InstantiationPlan>> resolveWitnessAll(
      List<ParsedType> targets, List<ContextInstance> context) {
    return Either.traverse(targets, target -> resolveWitness(target, context));
  }

  /** Compiles an InstantiationPlan into an Expr. */
  private static Expr<ParsedType> compile(InstantiationPlan plan) {
    return switch (plan) {
      case InstantiationPlan.PlanStep(var rule, var dependencies) ->
          switch (rule) {
            case ContextInstance(var instance, var type) -> new Expr.Lookup<>(type);
            case InstanceConstructor(var func) ->
                new Expr.InvokeConstructor<>(
                    func.java(), dependencies.stream().map(TypeClasses::compile).toList());
          };
    };
  }

  /**
   * Context for interpretation - maps keys to resolved instances. For our use case, keys are
   * ParsedTypes.
   */
  private record InterpretContext(List<ContextInstance> instances) {
    Either<InstantiationError, Object> lookup(ParsedType type) {
      return instances.stream()
          .filter(ci -> ci.type().equals(type))
          .findFirst()
          .map(ContextInstance::instance)
          .<Either<InstantiationError, Object>>map(Either::right)
          .orElseGet(() -> Either.left(new InstantiationError.LookupMiss(type)));
    }
  }

  /** Interprets an Expr with a given context. */
  private static Either<InstantiationError, Object> interpret(
      InterpretContext context, Expr<ParsedType> expr) {
    return switch (expr) {
      case Expr.Lookup<ParsedType>(var type) -> context.lookup(type);
      case Expr.InvokeConstructor<ParsedType>(var method, var args) ->
          Either.traverse(args, arg -> interpret(context, arg))
              .flatMap(
                  argValues ->
                      Either.call(() -> method.invoke(null, argValues.toArray()))
                          .mapLeft(e -> new InstantiationError.InvocationException(method, e)));
    };
  }

  /**
   * @implSpec <a href=
   *     "https://ghc.gitlab.haskell.org/ghc/doc/users_guide/exts/instances.html#overlapping-instances">6.8.8.5.
   *     Overlapping instances</a>
   */
  private static List<InstanceConstructor> reduceOverlapping(List<InstanceConstructor> candidates) {
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

  private static List<InstanceConstructor> findRules(ParsedType target) {
    return switch (target) {
      case ParsedType.App(var fun, var arg) -> Lists.concat(findRules(fun), findRules(arg));
      case ParsedType.Const(var java) -> rulesOf(java);
      case ParsedType.Var(var java) -> List.of();
      case ParsedType.ArrayOf(var elem) -> List.of();
      case ParsedType.Primitive(var java) -> List.of();
    };
  }

  private static List<InstanceConstructor> rulesOf(Class<?> cls) {
    return Arrays.stream(cls.getDeclaredMethods())
        .filter(TypeClasses::isWitnessMethod)
        .map(FuncType::parse)
        .map(InstanceConstructor::new)
        .toList();
  }

  private static boolean isWitnessMethod(Method m) {
    return m.accessFlags().contains(PUBLIC)
        && m.accessFlags().contains(STATIC)
        && m.isAnnotationPresent(TypeClass.Witness.class);
  }

  private sealed interface WitnessRule {
    Maybe<List<ParsedType>> tryMatch(ParsedType target);
  }

  private record ContextInstance(Object instance, ParsedType type) implements WitnessRule {
    @Override
    public Maybe<List<ParsedType>> tryMatch(ParsedType target) {
      return target.equals(type) ? Maybe.just(List.of()) : Maybe.nothing();
    }

    @Override
    public String toString() {
      return "context instance: " + type.format();
    }
  }

  private record InstanceConstructor(FuncType func) implements WitnessRule {
    public TypeClass.Witness.Overlap overlap() {
      return func.java().getAnnotation(TypeClass.Witness.class).overlap();
    }

    @Override
    public Maybe<List<ParsedType>> tryMatch(ParsedType target) {
      return Unification.unify(func.returnType(), target)
          .map(map -> Unification.substituteAll(map, func.paramTypes()));
    }

    @Override
    public String toString() {
      return func.format();
    }
  }
}
