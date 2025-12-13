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

  private sealed interface SummonError {
    record NotFound(ParsedType target) implements SummonError {}

    record Ambiguous(ParsedType target, List<Candidate> candidates) implements SummonError {}

    record Nested(ParsedType target, SummonError cause) implements SummonError {}

    default String format() {
      return switch (this) {
        case NotFound(ParsedType target) -> "No witness found for type: " + target.format();
        case Ambiguous(ParsedType target, List<Candidate> candidates) ->
            "Ambiguous witnesses found for type: "
                + target.format()
                + "\nCandidates:\n"
                + candidates.stream()
                    .map(c -> c.rule().toString())
                    .collect(Collectors.joining("\n"))
                    .indent(2);
        case Nested(ParsedType target, SummonError cause) ->
            "While summoning witness for type: "
                + target.format()
                + "\nCaused by: "
                + cause.format().indent(2);
      };
    }
  }

  /**
   * Summons a witness for the given target type using the staged resolution approach: parseType >>
   * resolveWitness >> compile >> interpret
   */
  private static Either<SummonError, Object> summon(
      ParsedType target, List<ContextInstance> context) {
    return resolveWitness(target, context)
        .map(TypeClasses::compile)
        .map(expr -> interpret(new InterpretContext(context), expr));
  }

  private record Candidate(WitnessRule rule, List<ParsedType> requirements) {}

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
  private static Either<SummonError, InstantiationPlan> resolveWitness(
      ParsedType target, List<ContextInstance> context) {
    return switch (ZeroOneMore.of(findCandidates(target, context))) {
      case ZeroOneMore.One<Candidate>(Candidate(var rule, var requirements)) ->
          resolveWitnessAll(requirements, context)
              .<InstantiationPlan>map(
                  dependencies -> new InstantiationPlan.PlanStep(rule, dependencies))
              .mapLeft(error -> new SummonError.Nested(target, error));
      case ZeroOneMore.Zero<Candidate>() -> Either.left(new SummonError.NotFound(target));
      case ZeroOneMore.More<Candidate>(var candidates) ->
          Either.left(new SummonError.Ambiguous(target, candidates));
    };
  }

  /** Resolves multiple ParsedTypes into a list of InstantiationPlans. */
  private static Either<SummonError, List<InstantiationPlan>> resolveWitnessAll(
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
    Object lookup(ParsedType type) {
      return instances.stream()
          .filter(ci -> ci.type().equals(type))
          .findFirst()
          .map(ContextInstance::instance)
          .orElseThrow(
              () -> new RuntimeException("Context lookup failed for type: " + type.format()));
    }
  }

  /** Interprets an Expr with a given context. */
  private static Object interpret(InterpretContext context, Expr<ParsedType> expr) {
    return switch (expr) {
      case Expr.Lookup<ParsedType>(var type) -> context.lookup(type);
      case Expr.InvokeConstructor<ParsedType>(var method, var args) -> {
        Object[] evaluatedArgs = args.stream().map(arg -> interpret(context, arg)).toArray();
        try {
          yield method.invoke(null, evaluatedArgs);
        } catch (Exception e) {
          throw new RuntimeException("Failed to invoke constructor: " + method, e);
        }
      }
    };
  }

  private static List<Candidate> findCandidates(ParsedType target, List<ContextInstance> context) {
    return Stream.<WitnessRule>concat(
            context.stream(), reduceOverlapping(findRules(target)).stream())
        .flatMap(
            rule ->
                rule
                    .tryMatch(target)
                    .map(requirements -> new Candidate(rule, requirements))
                    .stream())
        .toList();
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

    Object instantiate(List<Object> dependencies);
  }

  private record ContextInstance(Object instance, ParsedType type) implements WitnessRule {
    @Override
    public Maybe<List<ParsedType>> tryMatch(ParsedType target) {
      return target.equals(type) ? Maybe.just(List.of()) : Maybe.nothing();
    }

    @Override
    public Object instantiate(List<Object> dependencies) {
      return instance;
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
    public Object instantiate(List<Object> dependencies) {
      try {
        return func.java().invoke(null, dependencies.toArray());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public String toString() {
      return func.format();
    }
  }
}
