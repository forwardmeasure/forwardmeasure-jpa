package com.forwardmeasure.jpa.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.database.migration.api.DatabaseTarget;
import com.forwardmeasure.database.migration.api.MigrationPlan;
import com.forwardmeasure.database.migration.api.MigrationRequest;
import com.forwardmeasure.database.migration.liquibase.LiquibaseMigrationEngine;
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
import com.forwardmeasure.jpa.locking.entity.SystemLock;
import com.forwardmeasure.jpa.locking.service.SystemLockService;
import com.forwardmeasure.jpa.tenancy.FunctionalSchema;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlContainerConfiguration;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.util.List;
import java.util.Optional;
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

  // TenantScope now carries TenantDatabase directly - no TenantSchema/TenantId round-trip and no
  // TenantRegistry lookup needed for this test, since the alias is known up front.
  private static final TenantDatabase TENANT_DATABASE = TenantDatabase.forAlias("contracttest");
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
                  PostgreSqlContainerConfiguration.DEFAULT_MEMORY_SWAP_BYTES))
          .start();

  static {
    DATABASE.createSchema(SCHEMA.schemaName());
    new LiquibaseMigrationEngine(SpringJpaContractTest.class.getClassLoader())
        .migrate(
            new MigrationRequest(
                DATABASE.dataSource(),
                DatabaseTarget.schema(SCHEMA.schemaName()),
                MigrationPlan.liquibase(
                    "forwardmeasure-jpa", "db/changelog/forwardmeasure-jpa-contract-tests.xml")));
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", DATABASE::hostJdbcUrl);
    properties.add("spring.datasource.username", DATABASE::username);
    properties.add("spring.datasource.password", DATABASE::password);
    properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    properties.add("spring.jpa.open-in-view", () -> "false");
    properties.add("forwardmeasure.jpa.functional-schema", SCHEMA::name);
    properties.add("forwardmeasure.jpa.tenant-database.host", DATABASE::host);
    properties.add(
        "forwardmeasure.jpa.tenant-database.port", () -> String.valueOf(DATABASE.mappedPort()));
    properties.add("forwardmeasure.jpa.tenant-database.username", DATABASE::username);
    properties.add("forwardmeasure.jpa.tenant-database.password", DATABASE::password);
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
    try (TenantScope.Scope ignored = tenantScope.open(TENANT_DATABASE)) {
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
    try (TenantScope.Scope ignored = tenantScope.open(TENANT_DATABASE)) {
      assertTrue(actorService.count() >= 0L);
      assertThrows(
          org.springframework.transaction.IllegalTransactionStateException.class,
          () -> systemLocks.acquireLock("contract-lock"));
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
