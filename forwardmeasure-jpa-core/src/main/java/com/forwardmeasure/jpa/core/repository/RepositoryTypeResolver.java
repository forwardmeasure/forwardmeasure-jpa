/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
