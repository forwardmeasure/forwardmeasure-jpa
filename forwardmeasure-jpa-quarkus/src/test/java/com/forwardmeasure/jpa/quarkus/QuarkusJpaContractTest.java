package com.forwardmeasure.jpa.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.contract.JpaPersistenceContract;
import com.forwardmeasure.jpa.contract.JpaServiceContract;
import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.contract.repository.ContractOwnedEntityRepository;
import com.forwardmeasure.jpa.contract.service.ContractOwnedEntityService;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.locking.service.SystemLockService;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import io.agroal.api.AgroalDataSource;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(QuarkusPostgreSqlResource.class)
class QuarkusJpaContractTest {

    @Inject
    TenantScope tenantScope;

    @Inject
    UserTransaction transaction;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    @PersistenceUnitExtension
    QuarkusTenantConnectionResolver tenantConnections;

    @Inject
    ActorRepository actors;

    @Inject
    ActorService actorService;

    @Inject
    ContractOwnedEntityRepository ownedEntities;

    @Inject
    ContractOwnedEntityService ownedEntityService;

    @Inject
    SystemLockService systemLocks;

    @Inject
    TaskStatusHandler taskStatusHandler;

    @Test
    void executesTheSameRepositoriesAndServicesThroughQuarkus()
            throws Exception {
        try (TenantScope.Scope ignored =
                tenantScope.open(QuarkusPostgreSqlResource.TENANT)) {
            transaction.begin();
            try {
                assertNotNull(taskStatusHandler);
                var repositoryResult = JpaPersistenceContract.verify(
                        actors, ownedEntities);
                var serviceResult = JpaServiceContract.verify(
                        actorService, ownedEntityService);
                systemLocks.acquireLock("contract-lock");
                assertTrue(actors.findByUuid(
                        repositoryResult.actorUuid()).isPresent());
                assertTrue(actorService.findByUuid(
                        serviceResult.actorUuid()).isPresent());
                transaction.commit();
            } catch (Exception | Error failure) {
                transaction.rollback();
                throw failure;
            }
        }
    }

    @Test
    void unscopedPersistenceAndLockingFailClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> new QuarkusTenantResolver(
                        tenantScope).resolveTenantId());
        assertThrows(RuntimeException.class, actors::count);
        assertThrows(
                jakarta.transaction.TransactionalException.class,
                () -> systemLocks.acquireLock("contract-lock"));
    }

    @Test
    void servicesOwnTransactionsWhileLocksRequireACallerTransaction() {
        try (TenantScope.Scope ignored =
                tenantScope.open(QuarkusPostgreSqlResource.TENANT)) {
            assertTrue(actorService.count() >= 0L);
            assertThrows(
                    jakarta.transaction.TransactionalException.class,
                    () -> systemLocks.acquireLock("contract-lock"));
        }
    }

    @Test
    void resetsPooledConnectionAfterTenantUse() throws Exception {
        var provider = tenantConnections.resolve(
                QuarkusPostgreSqlResource.TENANT.value());
        var tenantConnection = provider.getConnection();
        assertEquals(
                QuarkusPostgreSqlResource.TENANT.value(),
                tenantConnection.getSchema());
        provider.closeConnection(tenantConnection);

        try (var pooledConnection = dataSource.getConnection()) {
            assertEquals(
                    TenantSchema.PUBLIC.value(),
                    pooledConnection.getSchema());
        }
    }
}
