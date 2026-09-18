package com.forwardmeasure.jpa.micronaut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.database.migration.api.DatabaseTarget;
import com.forwardmeasure.database.migration.api.MigrationPlan;
import com.forwardmeasure.database.migration.api.MigrationRequest;
import com.forwardmeasure.database.migration.liquibase.LiquibaseMigrationEngine;
import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.contract.JpaPersistenceContract;
import com.forwardmeasure.jpa.contract.JpaServiceContract;
import com.forwardmeasure.jpa.contract.repository.ContractOwnedEntityRepository;
import com.forwardmeasure.jpa.contract.service.ContractOwnedEntityService;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.locking.service.SystemLockService;
import com.forwardmeasure.jpa.tenancy.FunctionalSchema;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlContainerConfiguration;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@MicronautTest(startApplication = false, transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Introspected(
    packages = "com.forwardmeasure.jpa.contract.entity",
    includedAnnotations = Entity.class)
class MicronautJpaContractTest implements TestPropertyProvider {

  // TenantScope carries TenantDatabase directly now - no TenantSchema/TenantId round-trip needed.
  private static final TenantDatabase TENANT_DATABASE = TenantDatabase.forAlias("micronauttest");
  private static final FunctionalSchema SCHEMA = FunctionalSchema.OPENWORKFLOW;

  private static final PostgreSqlTestContainer DATABASE =
      new PostgreSqlTestContainer(
          new PostgreSqlContainerConfiguration(
              PostgreSqlContainerConfiguration.DEFAULT_IMAGE,
              TENANT_DATABASE.value(),
              "forwardmeasure",
              "forwardmeasure-test-only",
              Optional.empty(),
              List.of(),
              PostgreSqlContainerConfiguration.DEFAULT_MEMORY_BYTES,
              PostgreSqlContainerConfiguration.DEFAULT_MEMORY_SWAP_BYTES));

  private static boolean initialized;

  @Inject TenantScope tenantScope;

  @Inject TransactionOperations<Session> transactions;

  @Inject ActorRepository actors;

  @Inject ActorService actorService;

  @Inject ContractOwnedEntityRepository ownedEntities;

  @Inject ContractOwnedEntityService ownedEntityService;

  @Inject SystemLockService systemLocks;

  @Inject TaskStatusHandler taskStatusHandler;

  @Inject MultiTenantConnectionProvider<String> tenantConnections;

  @Override
  public synchronized Map<String, String> getProperties() {
    if (!initialized) {
      DATABASE.start();
      DATABASE.createSchema(SCHEMA.schemaName());
      new LiquibaseMigrationEngine(getClass().getClassLoader())
          .migrate(
              new MigrationRequest(
                  DATABASE.dataSource(),
                  DatabaseTarget.schema(SCHEMA.schemaName()),
                  MigrationPlan.liquibase(
                      "forwardmeasure-jpa", "db/changelog/forwardmeasure-jpa-contract-tests.xml")));
      initialized = true;
    }
    return Map.ofEntries(
        Map.entry("datasources.default.url", DATABASE.hostJdbcUrl()),
        Map.entry("datasources.default.username", DATABASE.username()),
        Map.entry("datasources.default.password", DATABASE.password()),
        Map.entry("datasources.default.driver-class-name", "org.postgresql.Driver"),
        Map.entry("jpa.default.properties.hibernate.hbm2ddl.auto", "none"),
        Map.entry("jpa.default.entity-scan.packages[0]", "com.forwardmeasure.jpa.identity.entity"),
        Map.entry("jpa.default.entity-scan.packages[1]", "com.forwardmeasure.jpa.locking.entity"),
        Map.entry("jpa.default.entity-scan.packages[2]", "com.forwardmeasure.jpa.asynctask.entity"),
        Map.entry("jpa.default.entity-scan.packages[3]", "com.forwardmeasure.jpa.contract.entity"),
        Map.entry("forwardmeasure.jpa.functional-schema", SCHEMA.name()),
        Map.entry("forwardmeasure.jpa.tenant-database.host", DATABASE.host()),
        Map.entry("forwardmeasure.jpa.tenant-database.port", String.valueOf(DATABASE.mappedPort())),
        Map.entry("forwardmeasure.jpa.tenant-database.username", DATABASE.username()),
        Map.entry("forwardmeasure.jpa.tenant-database.password", DATABASE.password()));
  }

  @Test
  void executesTheSameRepositoriesAndServicesThroughMicronaut() {
    try (TenantScope.Scope ignored = tenantScope.open(TENANT_DATABASE)) {
      var result =
          transactions.executeWrite(
              status -> {
                assertNotNull(taskStatusHandler);
                var repositoryResult = JpaPersistenceContract.verify(actors, ownedEntities);
                var serviceResult = JpaServiceContract.verify(actorService, ownedEntityService);
                systemLocks.acquireLock("contract-lock");
                assertTrue(actorService.findByUuid(serviceResult.actorUuid()).isPresent());
                return repositoryResult;
              });
      boolean present =
          transactions.executeRead(status -> actors.findByUuid(result.actorUuid()).isPresent());
      assertTrue(present);
    }
  }

  @Test
  void unscopedPersistenceAndLockingFailClosed() {
    assertThrows(RuntimeException.class, actors::count);
    assertThrows(RuntimeException.class, () -> systemLocks.acquireLock("contract-lock"));
  }

  @Test
  void servicesOwnTransactionsWhileLocksRequireACallerTransaction() {
    try (TenantScope.Scope ignored = tenantScope.open(TENANT_DATABASE)) {
      assertTrue(actorService.count() >= 0L);
      assertThrows(RuntimeException.class, () -> systemLocks.acquireLock("contract-lock"));
    }
  }

  @Test
  void tenantConnectionUsesTheFixedFunctionalSchemaNotATenantDerivedOne() throws Exception {
    var tenantConnection = tenantConnections.getConnection(TENANT_DATABASE.value());
    try {
      assertEquals(SCHEMA.schemaName(), tenantConnection.getSchema());
    } finally {
      tenantConnections.releaseConnection(TENANT_DATABASE.value(), tenantConnection);
    }
  }

  @Test
  void anyConnectionIsUsableForHibernatesTenantAgnosticOperations() throws Exception {
    var connection = tenantConnections.getAnyConnection();
    try {
      assertTrue(connection.isValid(2));
    } finally {
      tenantConnections.releaseAnyConnection(connection);
    }
  }

  @AfterAll
  static synchronized void stopDatabase() {
    if (initialized) {
      DATABASE.close();
      initialized = false;
    }
  }

  @Factory
  static class ContractBeans {

    @Singleton
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Singleton
    ContractOwnedEntityRepository contractRepository(EntityManager entityManager) {
      ContractOwnedEntityRepository repository = new ContractOwnedEntityRepository();
      repository.bindPersistenceContext(entityManager);
      return repository;
    }

    @Singleton
    ContractOwnedEntityService contractService(ContractOwnedEntityRepository repository) {
      return new ContractOwnedEntityService(repository);
    }
  }
}
