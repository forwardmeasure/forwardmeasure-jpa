package com.forwardmeasure.jpa.tenancy;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Carries {@link TenantDatabase} - the real physical routing target - not {@link TenantSchema}.
 * Deliberately not {@code TenantSchema} here (an earlier version of this interface used it,
 * converting to {@link TenantDatabase} locally inside each Hibernate connection provider): that
 * shape only ever worked because {@code TenantDatabase} used to be a pure function of the tenant's
 * UUID. Now that {@link TenantDatabase} is named after the tenant's own alias (not recoverable from
 * a bare UUID), carrying only a UUID through this scope would force a real registry lookup at every
 * single connection acquisition, even at the many call sites (JWT-authenticated HTTP/gRPC requests)
 * where the alias is already known and just needs to not be thrown away.
 */
public interface TenantScope {

  Optional<TenantDatabase> current();

  Scope open(TenantDatabase database);

  default TenantDatabase currentRequired() {
    return current()
        .orElseThrow(
            () ->
                new IllegalStateException("No tenant database is bound to the current execution"));
  }

  default void run(TenantDatabase database, Runnable operation) {
    try (Scope ignored = open(database)) {
      operation.run();
    }
  }

  default <T> T call(TenantDatabase database, Supplier<T> operation) {
    try (Scope ignored = open(database)) {
      return operation.get();
    }
  }

  interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
