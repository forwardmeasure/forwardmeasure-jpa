package com.forwardmeasure.jpa.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@WithPostgreSqlContainer(databaseName = "forwardmeasure_ds-registry")
class TenantDataSourceRegistryTest {

  @Test
  void dataSourceForConnectsToTheRealTenantDatabase(PostgreSqlTestContainer container)
      throws Exception {
    try (TenantDataSourceRegistry registry = new TenantDataSourceRegistry(realTemplate(container));
        Connection connection =
            registry.dataSourceFor(new TenantDatabase(container.databaseName())).getConnection()) {
      assertTrue(connection.isValid(2));
    }
  }

  @Test
  void sameTenantReusesTheSamePool(PostgreSqlTestContainer container) {
    try (TenantDataSourceRegistry registry =
        new TenantDataSourceRegistry(realTemplate(container))) {
      TenantDatabase database = new TenantDatabase(container.databaseName());
      assertSame(registry.dataSourceFor(database), registry.dataSourceFor(database));
    }
  }

  @Test
  void idlePoolsAreEvictedPastTheConfiguredTimeoutButNotBeforeIt() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    TenantDataSourceTemplate template = fakeTemplate(Duration.ofMinutes(10));
    try (TenantDataSourceRegistry registry = TenantDataSourceRegistry.forTesting(template, clock)) {
      TenantDatabase database = TenantDatabase.forAlias("ds-evict");
      DataSource first = registry.dataSourceFor(database);
      assertEquals(1, registry.poolCount());

      clock.advance(Duration.ofMinutes(5));
      registry.evictIdle();
      assertEquals(1, registry.poolCount(), "not idle long enough yet");

      clock.advance(Duration.ofMinutes(6));
      registry.evictIdle();
      assertEquals(0, registry.poolCount(), "idle past the configured timeout");

      DataSource second = registry.dataSourceFor(database);
      assertNotSame(first, second, "a fresh pool is built after eviction");
    }
  }

  @Test
  void accessingAPoolResetsItsIdleClock() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    TenantDataSourceTemplate template = fakeTemplate(Duration.ofMinutes(10));
    try (TenantDataSourceRegistry registry = TenantDataSourceRegistry.forTesting(template, clock)) {
      TenantDatabase database = TenantDatabase.forAlias("ds-touch");
      DataSource first = registry.dataSourceFor(database);

      clock.advance(Duration.ofMinutes(9));
      DataSource touched = registry.dataSourceFor(database);
      assertSame(first, touched, "still the same pool, just touched");
      registry.evictIdle();
      assertEquals(1, registry.poolCount(), "recently touched, must not be evicted");

      clock.advance(Duration.ofMinutes(9));
      registry.evictIdle();
      assertEquals(1, registry.poolCount(), "still within the timeout measured from the touch");
    }
  }

  @Test
  void distinctTenantsGetDistinctPools() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    TenantDataSourceTemplate template = fakeTemplate(Duration.ofMinutes(10));
    try (TenantDataSourceRegistry registry = TenantDataSourceRegistry.forTesting(template, clock)) {
      DataSource first = registry.dataSourceFor(TenantDatabase.forAlias("ds-first"));
      DataSource second = registry.dataSourceFor(TenantDatabase.forAlias("ds-second"));
      assertNotSame(first, second);
      assertEquals(2, registry.poolCount());
    }
  }

  @Test
  void closeShutsDownEveryOpenPoolAndClearsTheCache() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    TenantDataSourceTemplate template = fakeTemplate(Duration.ofMinutes(10));
    TenantDataSourceRegistry registry = TenantDataSourceRegistry.forTesting(template, clock);
    registry.dataSourceFor(TenantDatabase.forAlias("ds-close"));
    assertEquals(1, registry.poolCount());

    registry.close();

    assertEquals(0, registry.poolCount());
  }

  private static TenantDataSourceTemplate realTemplate(PostgreSqlTestContainer container) {
    return new TenantDataSourceTemplate(
        "jdbc:postgresql://" + container.host() + ":" + container.mappedPort() + "/",
        container.username(),
        container.password());
  }

  private static TenantDataSourceTemplate fakeTemplate(Duration idleEvictionTimeout) {
    return new TenantDataSourceTemplate(
        "jdbc:postgresql://localhost:1/", "user", "pass", 0, 1, idleEvictionTimeout);
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
