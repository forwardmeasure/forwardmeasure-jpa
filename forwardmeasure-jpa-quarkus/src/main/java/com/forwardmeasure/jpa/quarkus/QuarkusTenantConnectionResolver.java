package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.datasource.TenantDataSourceRegistry;
import com.forwardmeasure.jpa.tenancy.FunctionalSchema;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import io.quarkus.arc.Unremovable;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantConnectionResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

/**
 * Database-per-tenant, schema-per-product routing: each tenant identifier resolves to that tenant's
 * own physical database (via {@link TenantDataSourceRegistry}), then {@code
 * Connection#setSchema(String)} selects this product's own fixed {@link FunctionalSchema} within it
 * - not a tenant-derived schema. The tenant identifier Quarkus hands in IS the real {@link
 * TenantDatabase} value directly ({@code forwardmeasure_<alias>} - see {@code
 * QuarkusTenantResolver}, which reads it straight off {@code TenantScope}, itself opened with a
 * real {@link TenantDatabase} by whatever resolved the caller's identity) - no lookup needed here,
 * since {@code TenantScope} carries the real routing target, not a bare UUID.
 *
 * <p>Because every connection a resolved {@link ConnectionProvider} ever hands out comes from that
 * one tenant's own dedicated pool (never a pool shared with any other tenant), there is nothing to
 * reset on release - unlike the old shared-Agroal-datasource/{@code setSchema}-on-borrow model, a
 * connection returned to a tenant's own pool is guaranteed to be borrowed by that same tenant next
 * time regardless of what schema it was last left on.
 */
@PersistenceUnitExtension
@ApplicationScoped
@Unremovable
public class QuarkusTenantConnectionResolver implements TenantConnectionResolver {

  @Inject TenantDataSourceRegistry registry;
  @Inject FunctionalSchema functionalSchema;

  @Override
  public ConnectionProvider resolve(String tenantId) {
    TenantDatabase database = new TenantDatabase(tenantId);
    return new SchemaConnectionProvider(registry.dataSourceFor(database), functionalSchema);
  }

  private static final class SchemaConnectionProvider implements ConnectionProvider {

    private static final long serialVersionUID = 1L;

    private final DataSource dataSource;
    private final FunctionalSchema schema;

    private SchemaConnectionProvider(DataSource dataSource, FunctionalSchema schema) {
      this.dataSource = dataSource;
      this.schema = schema;
    }

    @Override
    public Connection getConnection() throws SQLException {
      Connection connection = dataSource.getConnection();
      try {
        connection.setSchema(schema.schemaName());
        return connection;
      } catch (SQLException exception) {
        connection.close();
        throw exception;
      }
    }

    @Override
    public void closeConnection(Connection connection) throws SQLException {
      if (connection == null || connection.isClosed()) {
        return;
      }
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
  }
}
