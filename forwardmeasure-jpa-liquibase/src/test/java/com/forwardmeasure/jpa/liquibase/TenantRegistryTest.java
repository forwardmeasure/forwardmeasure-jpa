package com.forwardmeasure.jpa.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.tenancy.Did;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The injected {@link PostgreSqlTestContainer} database is shared across every test method in this
 * class (confirmed the hard way: a first version of this test asserted {@code list().size() == 1}
 * and used the literal alias {@code "lux"} in more than one method, and collided both on that
 * unique-constraint and on the row count once methods ran in a different order). Every test below
 * therefore uses an alias (and derived DID) unique to that test's own randomly generated tenant,
 * and never asserts an exact {@link TenantRegistry#list()} size - only that its own tenant's record
 * is present with the expected values.
 */
@WithPostgreSqlContainer(databaseName = "platform_registry_contract")
class TenantRegistryTest {

  @Test
  void registersResolvesAndListsTenants(PostgreSqlTestContainer database) {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();

    String alias = randomAlias();
    Did tenantDid = didFor(alias);
    TenantId tenantId = TenantId.forDid(tenantDid);
    TenantDatabase tenantDatabase = TenantDatabase.forAlias(alias);

    assertEquals(Optional.empty(), registry.resolve(tenantId));
    assertEquals(Optional.empty(), registry.resolve(tenantDid));

    registry.register(tenantDid, alias, tenantDatabase, "default");

    assertEquals(Optional.of(tenantDatabase), registry.resolve(tenantId));
    assertEquals(Optional.of(tenantDatabase), registry.resolve(tenantDid));

    TenantRegistry.TenantRecord record = recordFor(registry, tenantId);
    assertEquals(tenantId, record.tenantId());
    assertEquals(tenantDid, record.tenantDid());
    assertEquals(alias, record.alias());
    assertEquals(tenantDatabase, record.database());
    assertEquals(TenantRegistry.Status.ACTIVE, record.status());
    assertEquals("default", record.cell());
  }

  @Test
  void reRegisteringTheSameTenantIsIdempotentAndUpdatesInPlace(PostgreSqlTestContainer database) {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();

    String alias = randomAlias();
    Did tenantDid = didFor(alias);
    TenantId tenantId = TenantId.forDid(tenantDid);
    TenantDatabase tenantDatabase = TenantDatabase.forAlias(alias);
    String renamedAlias = alias + "-renamed";
    registry.register(tenantDid, alias, tenantDatabase, "default");
    registry.register(tenantDid, renamedAlias, tenantDatabase, "default");

    List<TenantRegistry.TenantRecord> matching =
        registry.list().stream().filter(record -> record.tenantId().equals(tenantId)).toList();
    assertEquals(
        1, matching.size(), "re-registering must update the same row, not insert a second one");
    assertEquals(renamedAlias, matching.get(0).alias());
    assertEquals(tenantDid, matching.get(0).tenantDid());
  }

  @Test
  void resolveIsEmptyForAnUnregisteredTenant(PostgreSqlTestContainer database) {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();

    assertTrue(registry.resolve(new TenantId(UUID.randomUUID())).isEmpty());
    assertTrue(registry.resolve(didFor(randomAlias())).isEmpty());
  }

  @Test
  void registeringADifferentTenantWithAnAlreadyUsedAliasFailsCleanly(
      PostgreSqlTestContainer database) {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();

    String alias = randomAlias();
    registry.register(didFor(alias), alias, TenantDatabase.forAlias(alias), "default");

    Did otherDid = didFor(alias + "-other");
    assertThrows(
        TenantRegistryException.class,
        () ->
            registry.register(
                otherDid, alias, TenantDatabase.forAlias(alias + "-other"), "default"),
        "a second, different tenant must not be able to claim an alias already in use - and the"
            + " unique-constraint violation must surface as a domain exception, not a raw SQL one");
  }

  @Test
  void registeringADifferentTenantWithAnAlreadyUsedDatabaseNameFailsCleanly(
      PostgreSqlTestContainer database) {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();

    String alias = randomAlias();
    TenantDatabase sharedDatabase = TenantDatabase.forAlias(alias);
    registry.register(didFor(alias), alias, sharedDatabase, "default");

    String otherAlias = randomAlias();
    Did otherDid = didFor(otherAlias);
    assertThrows(
        TenantRegistryException.class,
        () -> registry.register(otherDid, otherAlias, sharedDatabase, "default"),
        "a second, different tenant must not be able to claim a database name already in use");
  }

  @Test
  void registerDefaultsCellWhenNotSpecified(PostgreSqlTestContainer database) {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();

    String alias = randomAlias();
    registry.register(didFor(alias), alias, TenantDatabase.forAlias(alias));

    assertEquals(
        TenantRegistry.DEFAULT_CELL, recordFor(registry, TenantId.forDid(didFor(alias))).cell());
  }

  @Test
  void migrateIsIdempotent(PostgreSqlTestContainer database) {
    TenantRegistry registry = new TenantRegistry(database.dataSource());

    // The changelog may already have been applied by another test method sharing this database -
    // assert idempotency (a second call applies nothing) rather than assuming this is the first
    // migrate() call against it.
    registry.migrate();
    assertEquals(0L, registry.migrate().appliedChangeCount());
  }

  private TenantRegistry.TenantRecord recordFor(TenantRegistry registry, TenantId tenantId) {
    return registry.list().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no registry record for tenant " + tenantId));
  }

  private Did didFor(String alias) {
    return Did.parse("did:web:" + alias + ".kriyagentic.com");
  }

  /** Starts with a letter, alphanumeric only - always a valid {@link TenantDatabase} alias. */
  private String randomAlias() {
    return "lux" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
