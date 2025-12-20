package com.garciat.typeclasses.impl;

import com.garciat.typeclasses.impl.utils.Rose;
import com.garciat.typeclasses.runtime.RuntimeWitnessConstructor;
import com.garciat.typeclasses.types.Either;
import java.lang.reflect.Method;
import java.util.List;

public final class WitnessInstantiation {
  private WitnessInstantiation() {}

  public static Expr compile(Rose<RuntimeWitnessConstructor> plan) {
    return switch (plan) {
      case Rose.Node(var constructor, var dependencies) ->
          new Expr.InvokeStaticMethod(
              constructor.java(),
              dependencies.stream().map(WitnessInstantiation::compile).toList());
    };
  }

  public sealed interface Expr {
    record InvokeStaticMethod(Method method, List<Expr> arguments) implements Expr {}
  }

  public static Either<InstantiationError, Object> interpret(Expr expr) {
    return switch (expr) {
      case Expr.InvokeStaticMethod(Method method, List<Expr> args) ->
          Either.traverse(args, WitnessInstantiation::interpret)
              .flatMap(
                  argValues ->
                      Either.call(() -> method.invoke(null, argValues.toArray()))
                          .mapLeft(e -> new InstantiationError.InvocationException(method, e)));
    };
  }

  public sealed interface InstantiationError {
    record InvocationException(Method method, Exception cause) implements InstantiationError {}

    default String format() {
      return switch (this) {
        case InvocationException(Method method, Exception cause) ->
            "Failed to invoke constructor: " + method + "\nCause: " + cause.getMessage();
      };
    }
  }
}
