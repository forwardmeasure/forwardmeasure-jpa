package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.datasource.TenantDataSourceRegistry;
import com.forwardmeasure.jpa.tenancy.FunctionalSchema;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import io.quarkus.arc.Unremovable;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantConnectionResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
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

  /**
   * The app's own default Agroal-managed {@code DataSource} (from {@code quarkus.datasource.*}) -
   * used only when {@code tenantId} is {@link TenantDatabase#UNBOUND_IDENTIFIER} ({@link
   * QuarkusTenantResolver#getDefaultTenantId()}'s sentinel for "no tenant scope open yet", e.g.
   * Hibernate's own boot-time dialect/version check). Unlike Hibernate's {@code
   * MultiTenantConnectionProvider} SPI (used by the Spring/Micronaut integrations, which get a
   * dedicated {@code getAnyConnection()} method for exactly this case, bypassing tenant resolution
   * entirely), Quarkus's {@link TenantConnectionResolver} SPI only exposes {@link #resolve(String)}
   * - real, confirmed live 2026-09-22: with no special case here, {@code
   * resolve(UNBOUND_IDENTIFIER)} tried to construct a real {@link TenantDatabase} from the
   * sentinel, which always throws (the sentinel deliberately never satisfies {@link
   * TenantDatabase}'s own validation - see its compact constructor), crashing every Quarkus
   * deployment's very first Hibernate bootstrap connection attempt. Injected as {@link Instance}
   * rather than directly, mirroring {@code QuarkusTenantDataSourceProducer#tenantRegistry}'s own
   * reasoning: not every consumer of this module configures a datasource, and {@link Instance}
   * stays resolvable at CDI build-time validation regardless - only actually evaluated the first
   * time this sentinel is really seen, which cannot happen for a deployment with no persistence
   * unit (and therefore no datasource) at all, since this class only activates as a {@link
   * PersistenceUnitExtension}.
   */
  @Inject Instance<DataSource> bootstrapDataSource;

  @Override
  public ConnectionProvider resolve(String tenantId) {
    if (TenantDatabase.UNBOUND_IDENTIFIER.equals(tenantId)) {
      return new PlainConnectionProvider(bootstrapDataSource.get());
    }
    TenantDatabase database = new TenantDatabase(tenantId);
    return new SchemaConnectionProvider(registry.dataSourceFor(database), functionalSchema);
  }

  private static final class PlainConnectionProvider implements ConnectionProvider {

    private static final long serialVersionUID = 1L;

    private final DataSource dataSource;

    private PlainConnectionProvider(DataSource dataSource) {
      this.dataSource = dataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
      return dataSource.getConnection();
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
