package com.forwardmeasure.jpa.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.jpa.asynctask.entity.AsyncTask;
import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.contract.JpaPersistenceContract;
import com.forwardmeasure.jpa.contract.JpaServiceContract;
import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity;
import com.forwardmeasure.jpa.contract.repository.ContractOwnedEntityRepository;
import com.forwardmeasure.jpa.contract.service.ContractOwnedEntityService;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.locking.entity.SystemLock;
import com.forwardmeasure.jpa.locking.service.SystemLockService;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.util.UUID;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = SpringJpaContractTest.TestApplication.class)
class SpringJpaContractTest {

  private static final PostgreSqlTestContainer DATABASE = new PostgreSqlTestContainer().start();

  private static final TenantSchema TENANT =
      TenantSchema.forTenant(new TenantId(UUID.fromString("20000000-0000-0000-0000-000000000001")));

  static {
    DATABASE.createSchema(TENANT.value());
    new TenantSchemaMigrator(
            DATABASE.dataSource(),
            "db/changelog/forwardmeasure-jpa-contract-tests.xml",
            SpringJpaContractTest.class.getClassLoader())
        .migrate(TENANT);
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", DATABASE::hostJdbcUrl);
    properties.add("spring.datasource.username", DATABASE::username);
    properties.add("spring.datasource.password", DATABASE::password);
    properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    properties.add("spring.jpa.open-in-view", () -> "false");
  }

  @Autowired TenantScope tenantScope;

  @Autowired TransactionTemplate transactions;

  @Autowired ActorRepository actors;

  @Autowired ActorService actorService;

  @Autowired ContractOwnedEntityRepository ownedEntities;

  @Autowired ContractOwnedEntityService ownedEntityService;

  @Autowired SystemLockService systemLocks;

  @Autowired TaskStatusHandler taskStatusHandler;

  @Autowired MultiTenantConnectionProvider<String> tenantConnections;

  @Test
  void executesTheSameRepositoriesAndServicesThroughSpring() {
    try (TenantScope.Scope ignored = tenantScope.open(TENANT)) {
      var result =
          transactions.execute(
              status -> {
                assertNotNull(taskStatusHandler);
                var repositoryResult = JpaPersistenceContract.verify(actors, ownedEntities);
                var serviceResult = JpaServiceContract.verify(actorService, ownedEntityService);
                systemLocks.acquireLock("contract-lock");
                assertTrue(actorService.findByUuid(serviceResult.actorUuid()).isPresent());
                return repositoryResult;
              });
      boolean present =
          transactions.execute(status -> actors.findByUuid(result.actorUuid()).isPresent());
      assertTrue(present);
    }
  }

  @Test
  void unscopedPersistenceAndLockingFailClosed() {
    assertThrows(RuntimeException.class, actors::count);
    assertThrows(
        org.springframework.transaction.IllegalTransactionStateException.class,
        () -> systemLocks.acquireLock("contract-lock"));
  }

  @Test
  void servicesOwnTransactionsWhileLocksRequireACallerTransaction() {
    try (TenantScope.Scope ignored = tenantScope.open(TENANT)) {
      assertTrue(actorService.count() >= 0L);
      assertThrows(
          org.springframework.transaction.IllegalTransactionStateException.class,
          () -> systemLocks.acquireLock("contract-lock"));
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
  static void stopDatabase() {
    DATABASE.close();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(
      basePackageClasses = {
        Actor.class,
        SystemLock.class,
        AsyncTask.class,
        ContractOwnedEntity.class
      })
  @Import({ContractOwnedEntityRepository.class, ContractOwnedEntityService.class})
  static class TestApplication {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
