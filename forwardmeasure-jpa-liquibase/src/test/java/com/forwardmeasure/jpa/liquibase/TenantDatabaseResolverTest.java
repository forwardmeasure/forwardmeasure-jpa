package com.forwardmeasure.jpa.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forwardmeasure.jpa.tenancy.Did;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@WithPostgreSqlContainer(databaseName = "tenant_database_resolver_contract")
class TenantDatabaseResolverTest {

  @Test
  void resolvesARegisteredTenantAndCachesTheResult(PostgreSqlTestContainer database)
      throws Exception {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();
    String alias = "lux" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    Did tenantDid = Did.parse("did:web:" + alias + ".kriyagentic.com");
    TenantId tenantId = TenantId.forDid(tenantDid);
    TenantDatabase expected = TenantDatabase.forAlias(alias);
    registry.register(tenantDid, alias, expected, "default");

    CountingDataSource counting = new CountingDataSource(database.dataSource());
    TenantDatabaseResolver resolver = new TenantDatabaseResolver(new TenantRegistry(counting));

    assertEquals(expected, resolver.resolve(tenantId));
    int queriesAfterFirstResolve = counting.connectionCount();
    assertEquals(expected, resolver.resolve(tenantId));
    assertEquals(
        queriesAfterFirstResolve,
        counting.connectionCount(),
        "a second resolve() for the same tenant must be served from cache, not a real query");
  }

  @Test
  void throwsForATenantThatWasNeverRegistered(PostgreSqlTestContainer database) {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();
    TenantDatabaseResolver resolver = new TenantDatabaseResolver(registry);

    TenantId unregistered = new TenantId(UUID.randomUUID());

    assertThrows(IllegalStateException.class, () -> resolver.resolve(unregistered));
  }

  @Test
  void resolvesATenantRegisteredAfterThisResolverWasConstructed(PostgreSqlTestContainer database) {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();
    TenantDatabaseResolver resolver = new TenantDatabaseResolver(registry);
    String alias = "lux" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    Did tenantDid = Did.parse("did:web:" + alias + ".kriyagentic.com");
    TenantId tenantId = TenantId.forDid(tenantDid);
    TenantDatabase expected = TenantDatabase.forAlias(alias);

    // Registered only now, after the resolver already exists - proves a cache miss re-queries
    // rather than permanently remembering an earlier "not found".
    registry.register(tenantDid, alias, expected, "default");

    assertEquals(expected, resolver.resolve(tenantId));
  }

  /** Counts real connections acquired, to prove caching actually avoids a second query. */
  private static final class CountingDataSource implements DataSource {
    private final DataSource delegate;
    private int connectionCount;

    private CountingDataSource(DataSource delegate) {
      this.delegate = delegate;
    }

    private int connectionCount() {
      return connectionCount;
    }

    @Override
    public Connection getConnection() throws SQLException {
      connectionCount++;
      return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      connectionCount++;
      return delegate.getConnection(username, password);
    }

    @Override
    public java.io.PrintWriter getLogWriter() throws SQLException {
      return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(java.io.PrintWriter out) throws SQLException {
      delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
      delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
      return delegate.getLoginTimeout();
    }

    @Override
    public java.util.logging.Logger getParentLogger() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return delegate.isWrapperFor(iface);
    }
  }
}
