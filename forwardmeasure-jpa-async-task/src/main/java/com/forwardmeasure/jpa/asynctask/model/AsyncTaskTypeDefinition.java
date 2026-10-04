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
package com.forwardmeasure.jpa.asynctask.model;

import java.util.Objects;

/** Stable metadata and default policy for an asynchronous task type. */
public record AsyncTaskTypeDefinition(
    String value, String resourceType, long defaultExpirySeconds, int defaultMaxAttempts) {

  public static final long DEFAULT_EXPIRY_SECONDS = 7L * 24 * 60 * 60;

  public static final int DEFAULT_MAX_ATTEMPTS = 3;

  public AsyncTaskTypeDefinition {
    requireText(value, "value");
    requireText(resourceType, "resourceType");
    if (value.length() > 50 || !value.matches("[a-z][a-z0-9_]*")) {
      throw new IllegalArgumentException("value must be snake_case and at most 50 characters");
    }
    if (resourceType.length() > 50) {
      throw new IllegalArgumentException("resourceType must be at most 50 characters");
    }
    if (defaultExpirySeconds <= 0) {
      throw new IllegalArgumentException("defaultExpirySeconds must be greater than zero");
    }
    if (defaultMaxAttempts <= 0) {
      throw new IllegalArgumentException("defaultMaxAttempts must be greater than zero");
    }
  }

  public static AsyncTaskTypeDefinition of(String value, String resourceType) {
    return new AsyncTaskTypeDefinition(
        value, resourceType, DEFAULT_EXPIRY_SECONDS, DEFAULT_MAX_ATTEMPTS);
  }

  public static AsyncTaskTypeDefinition of(
      String value, String resourceType, long defaultExpirySeconds, int defaultMaxAttempts) {
    return new AsyncTaskTypeDefinition(
        value, resourceType, defaultExpirySeconds, defaultMaxAttempts);
  }

  private static void requireText(String value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
