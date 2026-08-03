package com.forwardmeasure.jpa.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity;
import com.forwardmeasure.jpa.contract.ContractOwnedEntityService;
import com.forwardmeasure.jpa.contract.JpaPersistenceContract;
import com.forwardmeasure.jpa.contract.JpaServiceContract;
import com.forwardmeasure.jpa.identity.repository.JpaOwnedEntityRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.locking.SystemLockService;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import io.agroal.api.AgroalDataSource;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(QuarkusPostgreSqlResource.class)
class QuarkusJpaContractTest {

    @Inject
    TenantScope tenantScope;

    @Inject
    EntityManager entityManager;

    @Inject
    UserTransaction transaction;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    @PersistenceUnitExtension
    QuarkusTenantConnectionResolver tenantConnections;

    @Inject
    QuarkusActorRepository actors;

    @Inject
    ActorService actorService;

    @Inject
    SystemLockService systemLocks;

    @Inject
    QuarkusPanacheActorRepository panacheActors;

    @Inject
    ContractQuarkusPanacheRepository panacheOwned;

    @Test
    void executesPortableContractThroughQuarkusHibernate() throws Exception {
        try (TenantScope.Scope ignored =
                tenantScope.open(QuarkusPostgreSqlResource.TENANT)) {
            transaction.begin();
            try {
                var result = JpaPersistenceContract.verify(entityManager);
                var serviceResult = JpaServiceContract.verify(
                        actorService,
                        new ContractOwnedEntityService(
                                new JpaOwnedEntityRepository<>(
                                        ContractOwnedEntity.class,
                                        entityManager)));
                systemLocks.acquire("contract-lock");
                assertTrue(actors.findByUuid(result.actorUuid()).isPresent());
                assertTrue(actorService.findByUuid(
                        serviceResult.actorUuid()).isPresent());
                assertTrue(
                        panacheActors.findByUuid(result.actorUuid()).isPresent());
                assertTrue(panacheActors.existsByIdentity(
                        "contract-idp", "contract-user"));
                assertTrue(
                        panacheOwned.findByUuid(result.entityUuid()).isPresent());
                assertTrue(panacheOwned.existsByUuidAndOwnerId(
                        result.entityUuid(), result.actorId()));
                assertTrue(panacheOwned
                        .existsByIdAndOwnerSubjectIdentifier(
                                result.entityId(), "contract-user"));
                transaction.commit();
            } catch (Exception | Error failure) {
                transaction.rollback();
                throw failure;
            }
        }
    }

    @Test
    void unscopedPersistenceFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> new QuarkusTenantResolver(
                        tenantScope).resolveTenantId());
        assertThrows(RuntimeException.class, actors::count);
        assertThrows(
                jakarta.transaction.TransactionalException.class,
                () -> systemLocks.acquire("contract-lock"));
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
