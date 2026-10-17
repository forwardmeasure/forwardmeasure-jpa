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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.sql.Connection;
import java.sql.SQLException;
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

  @Test
  void serverDeadlineAppliesBeforeBeginAndDoesNotChangeTheOrdinaryPool(
      PostgreSqlTestContainer container) throws Exception {
    TenantDatabase database = new TenantDatabase(container.databaseName());
    try (TenantDataSourceRegistry registry =
        new TenantDataSourceRegistry(realTemplate(container))) {
      DataSource cachedProviderHandle = registry.dataSourceFor(database);
      try (TransactionDeadlineScope scope =
              TransactionDeadlineScope.open(database, Duration.ofSeconds(1));
          Connection connection = cachedProviderHandle.getConnection();
          var statement = connection.createStatement()) {
        try (var setting =
            statement.executeQuery(
                "select extract(epoch from current_setting('transaction_timeout')::interval)")) {
          assertTrue(setting.next());
          assertEquals(1, setting.getInt(1));
        }
        connection.setAutoCommit(false);
        statement.execute("select 1");
        Thread.sleep(1500);
        assertThrows(
            SQLException.class,
            () -> statement.execute("select 1"),
            "the server must end the transaction even while the application is paused");
      }
      try (Connection connection = cachedProviderHandle.getConnection();
          var statement = connection.createStatement();
          var setting = statement.executeQuery("select current_setting('transaction_timeout')")) {
        assertTrue(setting.next());
        assertEquals("0", setting.getString(1));
      }
    }
  }

  @Test
  void shortStatementsCannotRenewTheWholeTransactionDeadlineAndItsGuardsAreReleased(
      PostgreSqlTestContainer container) throws Exception {
    var database = new TenantDatabase(container.databaseName());
    long guard = 773400102L;
    try (var registry = new TenantDataSourceRegistry(realTemplate(container))) {
      var provider = registry.dataSourceFor(database);
      long started = System.nanoTime();
      try (var scope = TransactionDeadlineScope.open(database, Duration.ofSeconds(2));
          var connection = provider.getConnection();
          var statement = connection.createStatement()) {
        connection.setAutoCommit(false);
        statement.execute("select pg_advisory_xact_lock(" + guard + ")");
        assertThrows(
            SQLException.class,
            () -> {
              for (int step = 0; step < 5; step++) statement.execute("select pg_sleep(0.6)");
            },
            "Individually short statements cannot reset the pre-BEGIN transaction budget");
      }
      assertTrue(
          Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(5)) < 0,
          "The total deadline applies across the statement sequence");
      try (var connection = container.dataSource().getConnection();
          var statement = connection.createStatement();
          var result = statement.executeQuery("select pg_try_advisory_xact_lock(" + guard + ")")) {
        assertTrue(result.next());
        assertTrue(
            result.getBoolean(1),
            "The server must release the expired transaction's advisory guard");
      }
    }
  }

  @Test
  void anApplicationPauseAfterAStatementDoesNotStartANewServerDeadline(
      PostgreSqlTestContainer container) throws Exception {
    var database = new TenantDatabase(container.databaseName());
    try (var registry = new TenantDataSourceRegistry(realTemplate(container))) {
      var provider = registry.dataSourceFor(database);
      try (var scope = TransactionDeadlineScope.open(database, Duration.ofSeconds(3));
          var connection = provider.getConnection();
          var statement = connection.createStatement()) {
        connection.setAutoCommit(false);
        statement.execute("select pg_sleep(1)");
        // This deliberate application pause tests the absolute server deadline, not coordination
        // between concurrent writers. A fresh post-statement 3-second timeout would still be alive.
        Thread.sleep(2400);
        assertThrows(SQLException.class, () -> statement.execute("select 1"));
      }
      try (var connection = provider.getConnection();
          var statement = connection.createStatement();
          var result = statement.executeQuery("select current_setting('transaction_timeout')")) {
        assertTrue(result.next());
        assertEquals("0", result.getString(1), "The ordinary cached pool stays independent");
      }
    }
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
