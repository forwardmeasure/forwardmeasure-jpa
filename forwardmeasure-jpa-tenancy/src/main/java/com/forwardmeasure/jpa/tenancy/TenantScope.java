package com.forwardmeasure.jpa.tenancy;

import java.util.Optional;
import java.util.function.Supplier;

public interface TenantScope {

  Optional<TenantSchema> current();

  Scope open(TenantSchema schema);

  default TenantSchema currentRequired() {
    return current()
        .orElseThrow(
            () -> new IllegalStateException("No tenant schema is bound to the current execution"));
  }

  default void run(TenantSchema schema, Runnable operation) {
    try (Scope ignored = open(schema)) {
      operation.run();
    }
  }

  default <T> T call(TenantSchema schema, Supplier<T> operation) {
    try (Scope ignored = open(schema)) {
      return operation.get();
    }
  }

  interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
