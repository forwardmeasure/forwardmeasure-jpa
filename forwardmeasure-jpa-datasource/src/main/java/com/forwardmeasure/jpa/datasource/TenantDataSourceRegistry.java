package com.forwardmeasure.jpa.datasource;

import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
  private final Clock clock;
  private final Map<TenantDatabase, Entry> pools = new ConcurrentHashMap<>();
  private final ScheduledExecutorService evictionScheduler;

  public TenantDataSourceRegistry(TenantDataSourceTemplate template) {
    this(template, Clock.systemUTC(), true);
  }

  private TenantDataSourceRegistry(
      TenantDataSourceTemplate template, Clock clock, boolean selfScheduled) {
    this.template = Objects.requireNonNull(template, "template");
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
    return new TenantDataSourceRegistry(template, clock, false);
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
              return new Entry(buildDataSource(key), now);
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
                candidate.getValue().dataSource.close();
              }
              return idle;
            });
  }

  /** The number of currently-open per-tenant pools. */
  int poolCount() {
    return pools.size();
  }

  private HikariDataSource buildDataSource(TenantDatabase database) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(template.jdbcUrlPrefix() + database.value());
    config.setUsername(template.username());
    config.setPassword(template.password());
    config.setMinimumIdle(template.minimumIdle());
    config.setMaximumPoolSize(template.maximumPoolSize());
    config.setPoolName("tenant-" + database.value());
    // A tenant's database may not be reachable/provisioned at the instant this pool object is
    // created (e.g. eviction-timeout tests, or a registry built before its first real tenant
    // request arrives) - connections are still attempted lazily on first real borrow.
    config.setInitializationFailTimeout(-1);
    return new HikariDataSource(config);
  }

  @Override
  public void close() {
    if (evictionScheduler != null) {
      evictionScheduler.shutdownNow();
    }
    pools.values().forEach(entry -> entry.dataSource.close());
    pools.clear();
  }

  private static final class Entry {
    private final HikariDataSource dataSource;
    private final AtomicReference<Instant> lastAccess;

    private Entry(HikariDataSource dataSource, Instant createdAt) {
      this.dataSource = dataSource;
      this.lastAccess = new AtomicReference<>(createdAt);
    }
  }
}
