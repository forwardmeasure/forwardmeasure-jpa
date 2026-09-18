package com.forwardmeasure.jpa.liquibase;

import com.forwardmeasure.database.migration.api.DatabaseTarget;
import com.forwardmeasure.database.migration.api.MigrationPlan;
import com.forwardmeasure.database.migration.api.MigrationRequest;
import com.forwardmeasure.database.migration.api.MigrationResult;
import com.forwardmeasure.database.migration.liquibase.LiquibaseMigrationEngine;
import com.forwardmeasure.jpa.tenancy.Did;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * The authoritative tenant -&gt; database mapping, replacing the previous "list of tenants"
 * mechanism ({@code ProvisionedTenantSchemas} scanning {@code information_schema.schemata} for
 * {@code t_%} names) with a real, first-class registry table in a small, dedicated, non-tenant
 * "platform" database - separate from every tenant's own {@link TenantDatabase}.
 *
 * <p>{@link Did} is the canonical, universal <em>business</em> tenant identity every registration
 * is keyed by (see {@link TenantId#forDid(Did)}'s own javadoc for why); {@link TenantId} remains
 * the deterministically-derived UUID the table's primary key uses. {@link TenantDatabase} is
 * <strong>not</strong> deterministically derivable from {@link TenantId} - it is named after the
 * tenant's own alias for operability, and the alias is not recoverable from a UUID - so resolving a
 * tenant's database from only its {@link TenantId} (the common runtime case: JWT claims and gRPC
 * metadata carry a UUID, not an alias) genuinely requires the lookup this class provides, not just
 * bookkeeping. Provisioning code that already has the alias in hand (a migration Job iterating its
 * own tenant list) should call {@link TenantDatabase#forAlias(String)} directly instead - no lookup
 * needed there. See {@code TenantDatabaseResolver} (same package) for a cached wrapper around
 * {@link #resolve(TenantId)}, for call sites too frequent to tolerate a real query every time (e.g.
 * Hibernate's {@code CurrentTenantIdentifierResolver}, consulted on every session open).
 *
 * <p>This class owns only the registry itself (which tenant maps to which database, and that
 * tenant's lifecycle status) - it does not create tenant databases, schemas, or roles. Migrator
 * classes ({@code OpenWorkflowTenantMigrator} and its per-product siblings) call {@link #register}
 * once a tenant's database has actually been provisioned.
 */
public final class TenantRegistry {

  public static final String CHANGELOG = "db/changelog/platform-registry.xml";

  /** The only {@code cell} value in real use today - see this class's own javadoc. */
  public static final String DEFAULT_CELL = "default";

  private static final String PLAN_ID = "platform-registry";

  private final DataSource dataSource;
  private final LiquibaseMigrationEngine engine;
  private final MigrationPlan plan;

  public TenantRegistry(DataSource dataSource) {
    this(dataSource, Thread.currentThread().getContextClassLoader());
  }

  public TenantRegistry(DataSource dataSource, ClassLoader classLoader) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    this.engine = new LiquibaseMigrationEngine(Objects.requireNonNull(classLoader, "classLoader"));
    this.plan = MigrationPlan.liquibase(PLAN_ID, CHANGELOG);
  }

  /** Applies {@link #CHANGELOG} to the platform database's own public schema. Idempotent. */
  public MigrationResult migrate() {
    return engine.migrate(new MigrationRequest(dataSource, DatabaseTarget.schema("public"), plan));
  }

  /**
   * Convenience for {@link #register(Did, String, TenantDatabase, String)} with {@code cell}
   * defaulted to {@link #DEFAULT_CELL} - the only cell in use today, so no real caller needs to
   * name it explicitly yet.
   */
  public void register(Did tenantDid, String alias, TenantDatabase database) {
    register(tenantDid, alias, database, DEFAULT_CELL);
  }

  /**
   * Registers or re-registers a tenant, setting its status to {@link Status#ACTIVE}. Idempotent -
   * safe to call again for a tenant that's already registered (e.g. a re-run migration Job), as
   * long as the alias and database name are unchanged for that tenant's DID. {@code tenantId} is
   * derived from {@code tenantDid} via {@link TenantId#forDid(Did)}, not accepted as a separate
   * parameter - the two must never be allowed to drift apart for the same tenant.
   */
  public void register(Did tenantDid, String alias, TenantDatabase database, String cell) {
    Objects.requireNonNull(tenantDid, "tenantDid");
    requireNonBlank(alias, "alias");
    Objects.requireNonNull(database, "database");
    requireNonBlank(cell, "cell");
    TenantId tenantId = TenantId.forDid(tenantDid);
    String sql =
        "INSERT INTO tenant_registry (tenant_id, tenant_did, alias, database_name, status, cell,"
            + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP,"
            + " CURRENT_TIMESTAMP) ON CONFLICT (tenant_id) DO UPDATE SET "
            + "tenant_did = EXCLUDED.tenant_did, alias = EXCLUDED.alias, "
            + "database_name = EXCLUDED.database_name, status = EXCLUDED.status, "
            + "cell = EXCLUDED.cell, updated_at = CURRENT_TIMESTAMP";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, tenantId.value());
      statement.setString(2, tenantDid.value());
      statement.setString(3, alias);
      statement.setString(4, database.value());
      statement.setString(5, Status.ACTIVE.name());
      statement.setString(6, cell);
      statement.executeUpdate();
    } catch (SQLException exception) {
      throw new TenantRegistryException("could not register tenant " + tenantDid, exception);
    }
  }

  /** Convenience for {@code resolve(TenantId.forDid(tenantDid))} - see {@link TenantId#forDid}. */
  public Optional<TenantDatabase> resolve(Did tenantDid) {
    return resolve(TenantId.forDid(Objects.requireNonNull(tenantDid, "tenantDid")));
  }

  public Optional<TenantDatabase> resolve(TenantId tenantId) {
    Objects.requireNonNull(tenantId, "tenantId");
    String sql = "SELECT database_name FROM tenant_registry WHERE tenant_id = ? AND status = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, tenantId.value());
      statement.setString(2, Status.ACTIVE.name());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(new TenantDatabase(resultSet.getString("database_name")));
      }
    } catch (SQLException exception) {
      throw new TenantRegistryException("could not resolve tenant " + tenantId, exception);
    }
  }

  /** Every registered tenant, active or not, ordered by when it was first registered. */
  public List<TenantRecord> list() {
    String sql =
        "SELECT tenant_id, tenant_did, alias, database_name, status, cell, created_at FROM"
            + " tenant_registry ORDER BY created_at";
    List<TenantRecord> records = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        records.add(
            new TenantRecord(
                new TenantId((UUID) resultSet.getObject("tenant_id")),
                Did.parse(resultSet.getString("tenant_did")),
                resultSet.getString("alias"),
                new TenantDatabase(resultSet.getString("database_name")),
                Status.valueOf(resultSet.getString("status")),
                resultSet.getString("cell"),
                resultSet.getObject("created_at", OffsetDateTime.class)));
      }
    } catch (SQLException exception) {
      throw new TenantRegistryException("could not list tenants", exception);
    }
    return List.copyOf(records);
  }

  private static void requireNonBlank(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  public enum Status {
    PROVISIONING,
    ACTIVE,
    DEPROVISIONING,
    DELETED
  }

  public record TenantRecord(
      TenantId tenantId,
      Did tenantDid,
      String alias,
      TenantDatabase database,
      Status status,
      String cell,
      OffsetDateTime createdAt) {}
}
