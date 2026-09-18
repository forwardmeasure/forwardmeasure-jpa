package com.forwardmeasure.jpa.datasource;

import java.time.Duration;
import java.util.Objects;

/**
 * The connection parameters shared by every tenant's own pool - everything except the database name
 * itself, which {@link TenantDataSourceRegistry} substitutes in per tenant via {@link
 * com.forwardmeasure.jpa.tenancy.TenantDatabase#value()}.
 *
 * <p>{@code jdbcUrlPrefix} must be ready to have a database name appended directly (e.g. {@code
 * "jdbc:postgresql://host:5432/"}) - no database name, no {@code currentSchema}/schema-qualifying
 * query parameter of its own. Schema selection within a tenant's own database is unchanged from
 * today's model: callers still call {@code Connection#setSchema(String)} on the connection this
 * template's pools return, now with a fixed, product-static {@code FunctionalSchema} rather than a
 * tenant-derived one.
 */
public record TenantDataSourceTemplate(
    String jdbcUrlPrefix,
    String username,
    String password,
    int minimumIdle,
    int maximumPoolSize,
    Duration idleEvictionTimeout) {

  /**
   * Matches Pekko's own already-proven per-tenant pool sizing ({@code TenantPersistencePlugins}).
   */
  public static final int DEFAULT_MINIMUM_IDLE = 0;

  public static final int DEFAULT_MAXIMUM_POOL_SIZE = 3;
  public static final Duration DEFAULT_IDLE_EVICTION_TIMEOUT = Duration.ofMinutes(30);

  public TenantDataSourceTemplate {
    if (jdbcUrlPrefix == null || jdbcUrlPrefix.isBlank()) {
      throw new IllegalArgumentException("jdbcUrlPrefix must not be blank");
    }
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(password, "password");
    if (minimumIdle < 0) {
      throw new IllegalArgumentException("minimumIdle must not be negative");
    }
    if (maximumPoolSize < 1) {
      throw new IllegalArgumentException("maximumPoolSize must be at least 1");
    }
    Objects.requireNonNull(idleEvictionTimeout, "idleEvictionTimeout");
    if (idleEvictionTimeout.isNegative() || idleEvictionTimeout.isZero()) {
      throw new IllegalArgumentException("idleEvictionTimeout must be positive");
    }
  }

  /** Convenience for the default pool sizing/eviction timeout above. */
  public TenantDataSourceTemplate(String jdbcUrlPrefix, String username, String password) {
    this(
        jdbcUrlPrefix,
        username,
        password,
        DEFAULT_MINIMUM_IDLE,
        DEFAULT_MAXIMUM_POOL_SIZE,
        DEFAULT_IDLE_EVICTION_TIMEOUT);
  }
}
