package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.WitnessResolution.InstantiationPlan;
import com.garciat.typeclasses.impl.WitnessRule.ContextInstance;
import com.garciat.typeclasses.impl.WitnessRule.InstanceConstructor;
import com.garciat.typeclasses.types.Either;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class WitnessInstantiation {
  private WitnessInstantiation() {}

  /** Compiles an InstantiationPlan into an Expr. */
  public static Expr<ParsedType> compile(InstantiationPlan plan) {
    return switch (plan) {
      case InstantiationPlan.PlanStep(var rule, var dependencies) ->
          switch (rule) {
            case ContextInstance(var instance, var type) -> new Expr.Lookup<>(type);
            case InstanceConstructor(var func) ->
                new Expr.InvokeConstructor<>(
                    func.java(), dependencies.stream().map(WitnessInstantiation::compile).toList());
          };
    };
  }

  /**
   * Represents a reduced AST of interpretable Java operations. This is the compiled form of an
   * InstantiationPlan.
   */
  public sealed interface Expr<K> {
    record InvokeConstructor<K>(Method method, List<Expr<K>> arguments) implements Expr<K> {}

    record Lookup<K>(K key) implements Expr<K> {}
  }

  /** Interprets an Expr with a given context. */
  public static Either<InstantiationError, Object> interpret(
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
   * Context for interpretation - maps keys to resolved instances. For our use case, keys are
   * ParsedTypes.
   */
  public record InterpretContext(Map<ParsedType, Object> instances) {
    Either<InstantiationError, Object> lookup(ParsedType type) {
      return instances.containsKey(type)
          ? Either.right(instances.get(type))
          : Either.left(new InstantiationError.LookupMiss(type));
    }

    public static InterpretContext of(List<ContextInstance> context) {
      return new InterpretContext(
          context.stream()
              .collect(Collectors.toMap(ContextInstance::type, ContextInstance::instance)));
    }
  }

  public sealed interface InstantiationError {
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
}
