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
package com.forwardmeasure.jpa.datasource;

import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A bounded, idle-eviction-capable cache of per-tenant {@link DataSource}s, keyed by {@link
 * TenantDatabase} - the connection-layer half of the database-per-tenant redesign.
 *
 * <p>A JDBC connection is bound to one database for its whole lifetime, so the old "one shared
 * pool, {@code Connection#setSchema(String)} per tenant on borrow" model (schema-per-tenant, {@code
 * com.forwardmeasure.jpa.tenancy.TenantSchema}-routed) cannot work once each tenant has its own
 * physical database - this class replaces that single shared pool with one small pool per tenant,
 * built lazily on first use and closed once idle past {@link
 * TenantDataSourceTemplate#idleEvictionTimeout()}. Sizing each tenant's own pool small ({@code
 * minimumIdle=0}/{@code maximumPoolSize=3} by default) is deliberate: N tenants means N independent
 * pools, so total connections scale roughly {@code O(maximumPoolSize x active tenants)} per process
 * - the same real, already-flagged scaling characteristic Pekko's own {@code
 * TenantPersistencePlugins} accepted for its per-tenant journal/snapshot pools.
 *
 * <p>Schema selection within a tenant's own database is unchanged from today's model: callers still
 * call {@code Connection#setSchema(String)} on the connection a pool from this registry returns,
 * now with a fixed, product-static {@code FunctionalSchema} rather than a tenant-derived one.
 */
public final class TenantDataSourceRegistry implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(TenantDataSourceRegistry.class);

  private final TenantDataSourceTemplate template;
  private final TenantPoolFactory poolFactory;
  private final Clock clock;
  private final Map<TenantDatabase, Entry> pools = new ConcurrentHashMap<>();
  private final ScheduledExecutorService evictionScheduler;

  public TenantDataSourceRegistry(TenantDataSourceTemplate template) {
    this(template, TenantPoolFactory.HIKARI);
  }

  /**
   * A registry whose per-tenant pools come from {@code poolFactory}; see {@link TenantPoolFactory}.
   */
  public TenantDataSourceRegistry(
      TenantDataSourceTemplate template, TenantPoolFactory poolFactory) {
    this(template, poolFactory, Clock.systemUTC(), true);
  }

  private TenantDataSourceRegistry(
      TenantDataSourceTemplate template,
      TenantPoolFactory poolFactory,
      Clock clock,
      boolean selfScheduled) {
    this.template = Objects.requireNonNull(template, "template");
    this.poolFactory = Objects.requireNonNull(poolFactory, "poolFactory");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (selfScheduled) {
      this.evictionScheduler =
          Executors.newSingleThreadScheduledExecutor(
              runnable -> {
                Thread thread = new Thread(runnable, "tenant-datasource-eviction");
                thread.setDaemon(true);
                return thread;
              });
      long sweepMillis = Math.max(template.idleEvictionTimeout().toMillis() / 4, 1000L);
      evictionScheduler.scheduleWithFixedDelay(
          this::evictIdle, sweepMillis, sweepMillis, TimeUnit.MILLISECONDS);
    } else {
      this.evictionScheduler = null;
    }
  }

  /**
   * Test-only entry point - an explicit {@link Clock} with no self-scheduled eviction sweep, so
   * tests control exactly when {@link #evictIdle()} runs instead of racing a background timer.
   */
  static TenantDataSourceRegistry forTesting(TenantDataSourceTemplate template, Clock clock) {
    return new TenantDataSourceRegistry(template, TenantPoolFactory.HIKARI, clock, false);
  }

  /** Returns this tenant's own pooled {@link DataSource}, building it lazily on first use. */
  public DataSource dataSourceFor(TenantDatabase database) {
    Objects.requireNonNull(database, "database");
    Instant now = clock.instant();
    Entry entry =
        pools.computeIfAbsent(
            database,
            key -> {
              LOG.info("Opening tenant connection pool for database {}", key.value());
              return new Entry(poolFactory.create(key, template), now);
            });
    entry.lastAccess.set(now);
    return entry.dataSource;
  }

  /**
   * Closes and removes every pool idle past {@link TenantDataSourceTemplate#idleEvictionTimeout()}.
   */
  public void evictIdle() {
    Instant now = clock.instant();
    pools
        .entrySet()
        .removeIf(
            candidate -> {
              boolean idle =
                  candidate
                      .getValue()
                      .lastAccess
                      .get()
                      .plus(template.idleEvictionTimeout())
                      .isBefore(now);
              if (idle) {
                LOG.info(
                    "Evicting idle tenant connection pool for database {}",
                    candidate.getKey().value());
                close(candidate.getValue().dataSource);
              }
              return idle;
            });
  }

  /** The number of currently-open per-tenant pools. */
  int poolCount() {
    return pools.size();
  }

  @Override
  public void close() {
    if (evictionScheduler != null) {
      evictionScheduler.shutdownNow();
    }
    pools.values().forEach(entry -> close(entry.dataSource));
    pools.clear();
  }

  private static void close(DataSource dataSource) {
    if (dataSource instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception failure) {
        LOG.warn("Failed to close a tenant connection pool", failure);
      }
    }
  }

  private static final class Entry {
    private final DataSource dataSource;
    private final AtomicReference<Instant> lastAccess;

    private Entry(DataSource dataSource, Instant createdAt) {
      this.dataSource = dataSource;
      this.lastAccess = new AtomicReference<>(createdAt);
    }
  }
}
