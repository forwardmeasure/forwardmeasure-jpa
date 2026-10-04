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
package com.forwardmeasure.jpa.asynctask.converter;

import com.forwardmeasure.jpa.asynctask.model.AsyncTaskType;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskTypeDefinition;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Converter
public class AsyncTaskTypeConverter implements AttributeConverter<AsyncTaskType, String> {

  private static final Map<String, AsyncTaskType> TYPES = new ConcurrentHashMap<>();

  public static void register(AsyncTaskType... types) {
    Objects.requireNonNull(types, "types");
    for (AsyncTaskType type : types) {
      AsyncTaskType required = Objects.requireNonNull(type, "type");
      AsyncTaskType previous = TYPES.putIfAbsent(required.value(), required);
      if (previous != null && !previous.equals(required)) {
        throw new IllegalStateException(
            "Async task type is already registered: " + required.value());
      }
    }
  }

  @Override
  public String convertToDatabaseColumn(AsyncTaskType value) {
    return value == null ? null : value.value();
  }

  @Override
  public AsyncTaskType convertToEntityAttribute(String value) {
    if (value == null) {
      return null;
    }
    return TYPES.getOrDefault(value, new UnknownAsyncTaskType(value));
  }

  private record UnknownAsyncTaskType(String value) implements AsyncTaskType {

    @Override
    public AsyncTaskTypeDefinition definition() {
      return AsyncTaskTypeDefinition.of(value, "unknown");
    }

    @Override
    public String name() {
      return "UNKNOWN(" + value + ")";
    }
  }
}
