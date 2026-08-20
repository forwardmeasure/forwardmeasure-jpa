package com.forwardmeasure.jpa.asynctask.model;

/** Extensible application-defined asynchronous task type. */
public interface AsyncTaskType {

  AsyncTaskTypeDefinition definition();

  String name();

  default String value() {
    return definition().value();
  }

  default String resourceType() {
    return definition().resourceType();
  }

  default long defaultExpirySeconds() {
    return definition().defaultExpirySeconds();
  }

  default int defaultMaxAttempts() {
    return definition().defaultMaxAttempts();
  }
}
