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
