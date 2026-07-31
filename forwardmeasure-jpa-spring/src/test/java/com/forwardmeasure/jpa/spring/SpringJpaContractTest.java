package com.forwardmeasure.jpa.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forwardmeasure.jpa.contract.ContractOwnedEntity;
import com.forwardmeasure.jpa.contract.JpaPersistenceContract;
import com.forwardmeasure.jpa.identity.Actor;
import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.jpa.testcontainers.PostgreSqlTestDatabase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = SpringJpaContractTest.TestApplication.class)
class SpringJpaContractTest {

    private static final PostgreSqlTestDatabase DATABASE =
            new PostgreSqlTestDatabase().start();
    private static final TenantSchema TENANT = TenantSchema.forTenant(
            new TenantId(UUID.fromString(
                    "20000000-0000-0000-0000-000000000001")));

    static {
        DATABASE.createSchema(TENANT);
        new TenantSchemaMigrator(
                DATABASE.dataSource(),
                "db/changelog/forwardmeasure-jpa-contract-tests.xml",
                SpringJpaContractTest.class.getClassLoader())
                .migrate(TENANT);
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::jdbcUrl);
        properties.add("spring.datasource.username", DATABASE::username);
        properties.add("spring.datasource.password", DATABASE::password);
        properties.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        properties.add("spring.jpa.open-in-view", () -> "false");
    }

    @Autowired
    TenantScope tenantScope;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    SpringActorRepository actors;

    @Autowired
    MultiTenantConnectionProvider<String> tenantConnections;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void executesPortableContractAndSpringDataRepository() {
        try (TenantScope.Scope ignored = tenantScope.open(TENANT)) {
            var result = transactions.execute(status ->
                    JpaPersistenceContract.verify(entityManager));
            assertTrue(actors.findByUuid(result.actorUuid()).isPresent());
        }
    }

    @Test
    void unscopedRepositoryAccessFailsClosed() {
        assertThrows(RuntimeException.class, actors::count);
    }

    @Test
    void resetsPooledConnectionAfterTenantUse() throws Exception {
        var tenantConnection =
                tenantConnections.getConnection(TENANT.value());
        assertEquals(TENANT.value(), tenantConnection.getSchema());
        tenantConnections.releaseConnection(
                TENANT.value(), tenantConnection);

        var pooledConnection = tenantConnections.getAnyConnection();
        try {
            assertEquals(
                    TenantSchema.PUBLIC.value(),
                    pooledConnection.getSchema());
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
    @EntityScan(basePackageClasses = {Actor.class, ContractOwnedEntity.class})
    @EnableJpaRepositories(basePackageClasses = SpringActorRepository.class)
    static class TestApplication {
    }
}
