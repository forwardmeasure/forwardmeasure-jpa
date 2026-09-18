package com.forwardmeasure.jpa.micronaut;

import com.forwardmeasure.jpa.datasource.TenantDataSourceRegistry;
import com.forwardmeasure.jpa.tenancy.FunctionalSchema;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

/**
 * Database-per-tenant, schema-per-product routing: each tenant identifier resolves to that tenant's
 * own physical database (via {@link TenantDataSourceRegistry}), then {@code
 * Connection#setSchema(String)} selects this product's own fixed {@link FunctionalSchema} within it
 * - not a tenant-derived schema. The tenant identifier Hibernate hands in IS the real {@link
 * TenantDatabase} value directly ({@code forwardmeasure_<alias>} - see {@code
 * MicronautTenantIdentifierResolver}, which reads it straight off {@code TenantScope}, itself
 * opened with a real {@link TenantDatabase} by whatever resolved the caller's identity) - no lookup
 * needed here, since {@code TenantScope} carries the real routing target, not a bare UUID.
 *
 * <p>Because every connection this class ever hands out for a given tenant identifier comes from
 * that same tenant's own dedicated pool (never a pool shared with any other tenant), there is
 * nothing to reset on release - unlike the old shared-pool/{@code setSchema}-on-borrow model, a
 * connection returned to a tenant's own pool is guaranteed to be borrowed by that same tenant next
 * time regardless of what schema it was last left on.
 */
public final class MicronautSchemaConnectionProvider
    implements MultiTenantConnectionProvider<String> {

  private static final long serialVersionUID = 1L;

  private final TenantDataSourceRegistry registry;
  private final FunctionalSchema schema;
  private final DataSource bootstrapDataSource;

  /**
   * @param bootstrapDataSource used only for {@link #getAnyConnection()} - Hibernate's own
   *     tenant-agnostic operations (dialect resolution, DDL export tooling), never real tenant data
   *     access. Points at an administrative database, not any tenant's own database.
   */
  public MicronautSchemaConnectionProvider(
      TenantDataSourceRegistry registry, FunctionalSchema schema, DataSource bootstrapDataSource) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.schema = Objects.requireNonNull(schema, "schema");
    this.bootstrapDataSource =
        DelegatingDataSource.unwrapDataSource(
            Objects.requireNonNull(bootstrapDataSource, "bootstrapDataSource"));
  }

  @Override
  public Connection getAnyConnection() throws SQLException {
    return bootstrapDataSource.getConnection();
  }

  @Override
  public void releaseAnyConnection(Connection connection) throws SQLException {
    connection.close();
  }

  @Override
  public Connection getConnection(String tenantIdentifier) throws SQLException {
    TenantDatabase database = new TenantDatabase(tenantIdentifier);
    Connection connection = registry.dataSourceFor(database).getConnection();
    try {
      connection.setSchema(schema.schemaName());
      return connection;
    } catch (SQLException exception) {
      connection.close();
      throw exception;
    }
  }

  @Override
  public void releaseConnection(String tenantIdentifier, Connection connection)
      throws SQLException {
    connection.close();
  }

  @Override
  public boolean supportsAggressiveRelease() {
    return false;
  }

  @Override
  public boolean isUnwrappableAs(Class<?> unwrapType) {
    return unwrapType.isInstance(this);
  }

  @Override
  public <T> T unwrap(Class<T> unwrapType) {
    if (isUnwrappableAs(unwrapType)) {
      return unwrapType.cast(this);
    }
    throw new IllegalArgumentException("Unsupported unwrap type " + unwrapType.getName());
  }

  @Override
  public boolean handlesConnectionSchema() {
    return true;
  }
}
