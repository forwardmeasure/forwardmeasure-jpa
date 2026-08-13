package com.forwardmeasure.jpa.locking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.locking.entity.SystemLock;
import com.forwardmeasure.jpa.locking.repository.SystemLockRepository;
import com.forwardmeasure.jpa.locking.service.SystemLockService;
import com.forwardmeasure.jpa.locking.service.impl.SystemLockServiceImpl;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@WithPostgreSqlContainer(databaseName = "jpa_locking_contract")
class SystemLockPostgreSqlIntegrationTest {

    @Test
    void exposesLockNameAsItsJpaIdentifier() {
        SystemLock lock = SystemLock.builder()
                .lockName("initial-lock")
                .description("A provisioned database lock")
                .build();

        assertEquals("initial-lock", lock.getId());
        lock.setId("renamed-lock");
        assertEquals("renamed-lock", lock.getLockName());
        assertEquals("A provisioned database lock", lock.getDescription());
    }

    @Test
    void serializesCompetingTransactions(
            PostgreSqlTestContainer database) throws Exception {
        TenantSchema tenant = prepare(database);
        try (EntityManagerFactory entityManagers = entityManagers(
                database,
                tenant);
                var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch firstAcquired = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondAcquired = new CountDownLatch(1);

            var first = executor.submit(() -> inTransaction(
                    entityManagers,
                    service -> {
                        service.acquireLock("exclusive-test");
                        firstAcquired.countDown();
                        await(releaseFirst);
                    }));
            assertTrue(firstAcquired.await(5, TimeUnit.SECONDS));

            var second = executor.submit(() -> inTransaction(
                    entityManagers,
                    service -> {
                        service.acquireLock("exclusive-test");
                        secondAcquired.countDown();
                    }));

            assertFalse(
                    secondAcquired.await(300, TimeUnit.MILLISECONDS),
                    "Competing transaction acquired a lock before commit");
            releaseFirst.countDown();
            assertTrue(secondAcquired.await(5, TimeUnit.SECONDS));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectsUnknownAndInvalidLockNames(
            PostgreSqlTestContainer database) {
        TenantSchema tenant = prepare(database);
        try (EntityManagerFactory entityManagers = entityManagers(
                database,
                tenant)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> inTransaction(
                            entityManagers,
                            service -> service.acquireLock("not-provisioned")));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> inTransaction(
                            entityManagers,
                            service -> service.acquireLock(" ")));
        }
    }

    private TenantSchema prepare(PostgreSqlTestContainer database) {
        TenantSchema tenant = TenantSchema.forTenant(
                new TenantId(UUID.randomUUID()));
        database.createSchema(tenant.value());
        new TenantSchemaMigrator(
                database.dataSource(),
                "db/changelog/forwardmeasure-jpa-locking-test.xml",
                getClass().getClassLoader())
                .migrate(tenant);
        return tenant;
    }

    private EntityManagerFactory entityManagers(
            PostgreSqlTestContainer database,
            TenantSchema tenant) {
        return Persistence.createEntityManagerFactory(
                "forwardmeasure-jpa-locking-test",
                Map.of(
                        "jakarta.persistence.jdbc.url", database.hostJdbcUrl(),
                        "jakarta.persistence.jdbc.user", database.username(),
                        "jakarta.persistence.jdbc.password", database.password(),
                        "jakarta.persistence.jdbc.driver", "org.postgresql.Driver",
                        "hibernate.default_schema", tenant.value()));
    }

    private void inTransaction(
            EntityManagerFactory entityManagers,
            java.util.function.Consumer<SystemLockService> work) {
        EntityManager entityManager = entityManagers.createEntityManager();
        var transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            SystemLockRepository repository = new SystemLockRepository();
            repository.bindPersistenceContext(entityManager);
            work.accept(new SystemLockServiceImpl(repository));
            transaction.commit();
        } catch (RuntimeException | Error failure) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw failure;
        } finally {
            entityManager.close();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
