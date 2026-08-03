package com.forwardmeasure.jpa.micronaut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.contract.ContractOwnedEntityService;
import com.forwardmeasure.jpa.contract.JpaPersistenceContract;
import com.forwardmeasure.jpa.contract.JpaServiceContract;
import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity;
import com.forwardmeasure.jpa.identity.repository.JpaOwnedEntityRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.locking.SystemLockService;
import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import jakarta.persistence.Entity;
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

    private static final PostgreSqlTestContainer DATABASE =
            new PostgreSqlTestContainer();
    private static final TenantSchema TENANT = TenantSchema.forTenant(
            new TenantId(UUID.fromString(
                    "30000000-0000-0000-0000-000000000001")));
    private static boolean initialized;

    @Inject
    TenantScope tenantScope;

    @Inject
    TransactionOperations<Session> transactions;

    @Inject
    MicronautActorRepository actors;

    @Inject
    ActorService actorService;

    @Inject
    SystemLockService systemLocks;

    @Inject
    MultiTenantConnectionProvider<String> tenantConnections;

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
                Map.entry(
                        "datasources.default.url",
                        DATABASE.hostJdbcUrl()),
                Map.entry(
                        "datasources.default.username",
                        DATABASE.username()),
                Map.entry(
                        "datasources.default.password",
                        DATABASE.password()),
                Map.entry(
                        "datasources.default.driver-class-name",
                        "org.postgresql.Driver"),
                Map.entry(
                        "jpa.default.properties.hibernate.hbm2ddl.auto",
                        "none"),
                Map.entry(
                        "jpa.default.entity-scan.packages[0]",
                        "com.forwardmeasure.jpa.identity.entity"),
                Map.entry(
                        "jpa.default.entity-scan.packages[1]",
                        "com.forwardmeasure.jpa.locking.entity"),
                Map.entry(
                        "jpa.default.entity-scan.packages[2]",
                        "com.forwardmeasure.jpa.contract.entity"));
    }

    @Test
    void executesPortableContractAndMicronautDataRepository() {
        try (TenantScope.Scope ignored = tenantScope.open(TENANT)) {
            var result = transactions.executeWrite(status -> {
                Session session = status.getConnection();
                var repositoryResult = JpaPersistenceContract.verify(session);
                var serviceResult = JpaServiceContract.verify(
                        actorService,
                        new ContractOwnedEntityService(
                                new JpaOwnedEntityRepository<>(
                                        ContractOwnedEntity.class,
                                        session)));
                systemLocks.acquire("contract-lock");
                assertTrue(actorService.findByUuid(
                        serviceResult.actorUuid()).isPresent());
                return repositoryResult;
            });
            assertTrue(actors.findByUuid(result.actorUuid()).isPresent());
        }
    }

    @Test
    void unscopedRepositoryAccessFailsClosed() {
        assertThrows(RuntimeException.class, actors::count);
        assertThrows(
                RuntimeException.class,
                () -> systemLocks.acquire("contract-lock"));
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
    static synchronized void stopDatabase() {
        if (initialized) {
            DATABASE.close();
            initialized = false;
        }
    }
}
