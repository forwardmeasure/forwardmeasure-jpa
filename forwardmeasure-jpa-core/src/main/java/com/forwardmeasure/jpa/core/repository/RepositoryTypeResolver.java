package com.forwardmeasure.jpa.core.repository;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

final class RepositoryTypeResolver {

  private RepositoryTypeResolver() {}

  static Class<?> resolveEntityClass(Class<?> repositoryClass) {
    Map<TypeVariable<?>, Type> bindings = new HashMap<>();
    Type current = repositoryClass;
    while (current != null && current != Object.class) {
      if (current instanceof ParameterizedType parameterized) {
        Class<?> raw = (Class<?>) parameterized.getRawType();
        TypeVariable<?>[] variables = raw.getTypeParameters();
        Type[] arguments = parameterized.getActualTypeArguments();
        for (int index = 0; index < variables.length; index++) {
          bindings.put(variables[index], resolve(arguments[index], bindings));
        }
        if (raw == AbstractBaseRepository.class) {
          return toClass(resolve(arguments[0], bindings));
        }
        current = raw.getGenericSuperclass();
      } else if (current instanceof Class<?> type) {
        current = type.getGenericSuperclass();
      } else {
        break;
      }
    }
    throw new IllegalStateException(
        "Unable to resolve the entity type for repository " + repositoryClass.getName());
  }

  private static Type resolve(Type value, Map<TypeVariable<?>, Type> bindings) {
    Type resolved = value;
    while (resolved instanceof TypeVariable<?> variable && bindings.containsKey(variable)) {
      resolved = bindings.get(variable);
    }
    return resolved;
  }

  private static Class<?> toClass(Type value) {
    if (value instanceof Class<?> type) {
      return type;
    }
    if (value instanceof ParameterizedType parameterized
        && parameterized.getRawType() instanceof Class<?> type) {
      return type;
    }
    throw new IllegalStateException("Unsupported repository entity type " + value.getTypeName());
  }
}
