package com.garciat.typeclasses.impl;

import static java.lang.reflect.AccessFlag.PUBLIC;
import static java.lang.reflect.AccessFlag.STATIC;

import com.garciat.typeclasses.api.TypeClass;
import com.garciat.typeclasses.impl.WitnessRule.InstanceConstructor;
import com.garciat.typeclasses.impl.utils.Lists;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class ConstructorLookup {
  public static List<InstanceConstructor> findRules(ParsedType target) {
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
        .filter(ConstructorLookup::isWitnessMethod)
        .map(FuncType::parse)
        .map(InstanceConstructor::new)
        .toList();
  }

  private static boolean isWitnessMethod(Method m) {
    return m.accessFlags().contains(PUBLIC)
        && m.accessFlags().contains(STATIC)
        && m.isAnnotationPresent(TypeClass.Witness.class);
  }
}
