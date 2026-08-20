package com.forwardmeasure.jpa.micronaut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.contract.JpaPersistenceContract;
import com.forwardmeasure.jpa.contract.JpaServiceContract;
import com.forwardmeasure.jpa.contract.repository.ContractOwnedEntityRepository;
import com.forwardmeasure.jpa.contract.service.ContractOwnedEntityService;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.locking.service.SystemLockService;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.jpa.tenancy.TenantScope;
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
import java.util.Map;
import java.util.UUID;
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

  private static final PostgreSqlTestContainer DATABASE = new PostgreSqlTestContainer();

  private static final TenantSchema TENANT =
      TenantSchema.forTenant(new TenantId(UUID.fromString("30000000-0000-0000-0000-000000000001")));

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
      DATABASE.createSchema(TENANT.value());
      new TenantSchemaMigrator(
              DATABASE.dataSource(),
              "db/changelog/forwardmeasure-jpa-contract-tests.xml",
              getClass().getClassLoader())
          .migrate(TENANT);
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
        Map.entry("jpa.default.entity-scan.packages[3]", "com.forwardmeasure.jpa.contract.entity"));
  }

  @Test
  void executesTheSameRepositoriesAndServicesThroughMicronaut() {
    try (TenantScope.Scope ignored = tenantScope.open(TENANT)) {
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
    try (TenantScope.Scope ignored = tenantScope.open(TENANT)) {
      assertTrue(actorService.count() >= 0L);
      assertThrows(RuntimeException.class, () -> systemLocks.acquireLock("contract-lock"));
    }
  }

  @Test
  void resetsPooledConnectionAfterTenantUse() throws Exception {
    var tenantConnection = tenantConnections.getConnection(TENANT.value());
    assertEquals(TENANT.value(), tenantConnection.getSchema());
    tenantConnections.releaseConnection(TENANT.value(), tenantConnection);

    var pooledConnection = tenantConnections.getAnyConnection();
    try {
      assertEquals(TenantSchema.PUBLIC.value(), pooledConnection.getSchema());
    } finally {
      tenantConnections.releaseAnyConnection(pooledConnection);
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
