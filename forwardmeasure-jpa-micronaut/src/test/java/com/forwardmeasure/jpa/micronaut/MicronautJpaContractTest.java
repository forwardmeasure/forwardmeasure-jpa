package com.forwardmeasure.jpa.micronaut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forwardmeasure.jpa.contract.JpaPersistenceContract;
import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.jpa.testcontainers.PostgreSqlTestDatabase;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@MicronautTest(startApplication = false, transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Introspected(
        packages = "com.forwardmeasure.jpa.contract",
        includedAnnotations = Entity.class)
class MicronautJpaContractTest implements TestPropertyProvider {

    private static final PostgreSqlTestDatabase DATABASE =
            new PostgreSqlTestDatabase();
    private static final TenantSchema TENANT = TenantSchema.forTenant(
            new TenantId(UUID.fromString(
                    "30000000-0000-0000-0000-000000000001")));
    private static boolean initialized;

    @Inject
    TenantScope tenantScope;

    @Inject
    SessionFactory sessions;

    @Inject
    MicronautActorRepository actors;

    @Inject
    MultiTenantConnectionProvider<String> tenantConnections;

    @Override
    public synchronized Map<String, String> getProperties() {
        if (!initialized) {
            DATABASE.start();
            DATABASE.createSchema(TENANT);
            new TenantSchemaMigrator(
                    DATABASE.dataSource(),
                    "db/changelog/forwardmeasure-jpa-contract-tests.xml",
                    getClass().getClassLoader())
                    .migrate(TENANT);
            initialized = true;
        }
        return Map.of(
                "datasources.default.url", DATABASE.jdbcUrl(),
                "datasources.default.username", DATABASE.username(),
                "datasources.default.password", DATABASE.password(),
                "datasources.default.driver-class-name", "org.postgresql.Driver",
                "jpa.default.properties.hibernate.hbm2ddl.auto", "none",
                "jpa.default.entity-scan.packages[0]",
                "com.forwardmeasure.jpa.identity",
                "jpa.default.entity-scan.packages[1]",
                "com.forwardmeasure.jpa.contract");
    }

    @Test
    void executesPortableContractAndMicronautDataRepository() {
        try (TenantScope.Scope ignored = tenantScope.open(TENANT);
                var session = sessions.openSession()) {
            var transaction = session.beginTransaction();
            var result = JpaPersistenceContract.verify(session);
            transaction.commit();
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
    static synchronized void stopDatabase() {
        if (initialized) {
            DATABASE.close();
            initialized = false;
        }
    }
}
