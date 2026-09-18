package com.forwardmeasure.jpa.liquibase;

import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantId;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches {@link TenantRegistry#resolve(TenantId)} - a tenant's own database name is permanent once
 * assigned (an {@link TenantRegistry.Status#ACTIVE} tenant is never renamed to a different database
 * by this codebase), so a positive resolution is safe to cache forever; only a miss re-queries,
 * which also makes a tenant provisioned after this process started resolvable on its very first
 * real request rather than requiring a restart. Exists because runtime tenant-scoped connection
 * resolution (Hibernate's {@code CurrentTenantIdentifierResolver}, consulted on every session open;
 * Pekko actor entity recovery) happens far too frequently to tolerate a real query against the
 * platform database on every call - unlike {@code TenantDataSourceRegistry}'s own idle-eviction
 * (which closes unused connection pools, a genuinely different, reversible concern), nothing here
 * ever needs to be evicted.
 */
public final class TenantDatabaseResolver {
  private final TenantRegistry registry;
  private final ConcurrentHashMap<TenantId, TenantDatabase> cache = new ConcurrentHashMap<>();

  public TenantDatabaseResolver(TenantRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  private TenantDatabaseResolver(Map<TenantId, TenantDatabase> resolutions) {
    this.registry = null;
    this.cache.putAll(resolutions);
  }

  /**
   * A resolver whose answers are fixed up front rather than looked up - for unit tests exercising
   * tenant-scoped behavior (e.g. per-tenant cache isolation) without a real {@link TenantRegistry}/
   * database. Never queries a registry; a miss fails the same way a genuinely unresolvable tenant
   * would in production.
   */
  public static TenantDatabaseResolver preResolved(Map<TenantId, TenantDatabase> resolutions) {
    return new TenantDatabaseResolver(Map.copyOf(resolutions));
  }

  /**
   * @throws IllegalStateException if no active tenant is registered for {@code tenantId} - a
   *     wiring/data-integrity bug (every real caller only ever holds a {@code tenantId} that came
   *     from a verified JWT or a trusted internal caller, both of which imply the tenant was
   *     already provisioned), not a normal "not found" case to route around.
   */
  public TenantDatabase resolve(TenantId tenantId) {
    Objects.requireNonNull(tenantId, "tenantId");
    TenantDatabase cached = cache.get(tenantId);
    if (cached != null) {
      return cached;
    }
    if (registry == null) {
      throw new IllegalStateException("No active tenant registered for " + tenantId.value());
    }
    TenantDatabase resolved =
        registry
            .resolve(tenantId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No active tenant registered for " + tenantId.value()));
    cache.put(tenantId, resolved);
    return resolved;
  }
}
