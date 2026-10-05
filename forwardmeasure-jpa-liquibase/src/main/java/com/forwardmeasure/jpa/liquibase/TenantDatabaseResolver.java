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
package com.forwardmeasure.jpa.liquibase;

import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantId;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves only ACTIVE tenants against the authoritative registry on every call. Physical
 * connection pools may be reused, but tenant lifecycle authorization must never be cached.
 */
public final class TenantDatabaseResolver {
  private final TenantRegistry registry;
  private final Map<TenantId, TenantDatabase> fixedResolutions;

  public TenantDatabaseResolver(TenantRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.fixedResolutions = Map.of();
  }

  private TenantDatabaseResolver(Map<TenantId, TenantDatabase> resolutions) {
    this.registry = null;
    this.fixedResolutions = resolutions;
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

  /** Rejects unregistered or inactive tenants, including tenants deactivated since a prior call. */
  public TenantDatabase resolve(TenantId tenantId) {
    Objects.requireNonNull(tenantId, "tenantId");
    return (registry == null
            ? java.util.Optional.ofNullable(fixedResolutions.get(tenantId))
            : registry.resolve(tenantId))
        .orElseThrow(
            () -> new IllegalStateException("No active tenant registered for " + tenantId.value()));
  }
}
