package com.forwardmeasure.jpa.asynctask.model;

import java.util.Locale;

/** Durable lifecycle state for an asynchronous task. */
public enum AsyncTaskStatus {
  ACCEPTED,
  PROCESSING,
  COMPLETED,
  FAILED,
  CANCELLED,
  SKIPPED;

  public String databaseValue() {
    return name();
  }

  public String apiValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == CANCELLED || this == SKIPPED;
  }

  public static AsyncTaskStatus fromDatabaseValue(String value) {
    if ("PENDING".equals(value)) {
      return ACCEPTED;
    }
    return valueOf(value);
  }

  public static AsyncTaskStatus fromApiValue(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
