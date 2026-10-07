/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.forwardmeasure.jpa.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.contract.JpaPersistenceContract;
import com.forwardmeasure.jpa.contract.JpaServiceContract;
import com.forwardmeasure.jpa.contract.repository.ContractOwnedEntityRepository;
import com.forwardmeasure.jpa.contract.service.ContractOwnedEntityService;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.locking.service.SystemLockService;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
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

  @Inject TenantScope tenantScope;

  @Inject com.forwardmeasure.jpa.core.repository.JpaRepositoryContext repositoryContext;

  @Inject com.forwardmeasure.jpa.core.repository.JpaRepositoryTransactions repositoryTransactions;

  @Inject UserTransaction transaction;

  @Inject AgroalDataSource dataSource;

  @Inject @PersistenceUnitExtension QuarkusTenantConnectionResolver tenantConnections;

  @Inject ActorRepository actors;

  @Inject ActorService actorService;

  @Inject ContractOwnedEntityRepository ownedEntities;

  @Inject ContractOwnedEntityService ownedEntityService;

  @Inject SystemLockService systemLocks;

  @Inject TaskStatusHandler taskStatusHandler;

  @Test
  void producedRepositoryContextUsesTheManagedTenantTransaction() throws Exception {
    assertNotNull(repositoryTransactions);
    try (TenantScope.Scope ignored = tenantScope.open(QuarkusPostgreSqlResource.TENANT_ID)) {
      transaction.begin();
      try {
        var repository =
            repositoryContext.create(
                entityManager -> {
                  var result = new ActorRepository();
                  result.bindPersistenceContext(entityManager);
                  return result;
                });
        assertEquals(actors.count(), repository.count());
      } finally {
        transaction.rollback();
      }
    }
  }

  @Test
  void executesTheSameRepositoriesAndServicesThroughQuarkus() throws Exception {
    try (TenantScope.Scope ignored = tenantScope.open(QuarkusPostgreSqlResource.TENANT_ID)) {
      transaction.begin();
      try {
        assertNotNull(taskStatusHandler);
        var repositoryResult = JpaPersistenceContract.verify(actors, ownedEntities);
        var serviceResult = JpaServiceContract.verify(actorService, ownedEntityService);
        systemLocks.acquireLock("contract-lock");
        assertTrue(actors.findByUuid(repositoryResult.actorUuid()).isPresent());
        assertTrue(actorService.findByUuid(serviceResult.actorUuid()).isPresent());
        transaction.commit();
      } catch (Exception | Error failure) {
        transaction.rollback();
        throw failure;
      }
    }
  }

  /**
   * A rolled-back transaction leaves nothing in the tenant database. Quarkus runs Hibernate under
   * JTA, so this only holds if tenant connections are enlisted in the JTA transaction; plain
   * auto-commit connections would have committed each flushed statement already.
   */
  @Test
  void rollingBackATransactionDiscardsItsTenantDatabaseWrites() throws Exception {
    java.util.UUID actorUuid;
    java.util.UUID entityUuid;
    try (TenantScope.Scope ignored = tenantScope.open(QuarkusPostgreSqlResource.TENANT_ID)) {
      transaction.begin();
      try {
        var actor = new com.forwardmeasure.jpa.identity.entity.Actor();
        actor.setSubjectIdentifier("rollback-user");
        actor.setIdentityProvider("contract-idp");
        actor.setType(com.forwardmeasure.jpa.identity.entity.IdentityType.HUMAN);
        actor.setEmail("rollback@example.test");
        actors.persist(actor);
        var entity = new com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity();
        entity.setName("rolled-back");
        entity.setOwner(actor);
        ownedEntities.persistAndFlush(entity);
        actorUuid = actor.getUuid();
        entityUuid = entity.getUuid();
      } finally {
        transaction.rollback();
      }

      transaction.begin();
      try {
        assertTrue(actors.findByUuid(actorUuid).isEmpty(), "rolled-back actor was committed");
        assertTrue(
            ownedEntities.findByUuid(entityUuid).isEmpty(), "rolled-back entity was committed");
      } finally {
        transaction.rollback();
      }
    }
  }

  @Test
  void unscopedPersistenceAndLockingFailClosed() {
    QuarkusTenantResolver resolver = new QuarkusTenantResolver(tenantScope);
    assertEquals(TenantDatabase.UNBOUND_IDENTIFIER, resolver.getDefaultTenantId());
    assertThrows(IllegalStateException.class, resolver::resolveTenantId);
    assertThrows(RuntimeException.class, actors::count);
    assertThrows(
        jakarta.transaction.TransactionalException.class,
        () -> systemLocks.acquireLock("contract-lock"));
  }

  @Test
  void resolvesTheExplicitTenantScopeForHibernate() {
    QuarkusTenantResolver resolver = new QuarkusTenantResolver(tenantScope);
    try (TenantScope.Scope ignored = tenantScope.open(QuarkusPostgreSqlResource.TENANT_ID)) {
      assertEquals(QuarkusPostgreSqlResource.TENANT_ID.toString(), resolver.resolveTenantId());
    }
  }

  @Test
  void servicesOwnTransactionsWhileLocksRequireACallerTransaction() {
    try (TenantScope.Scope ignored = tenantScope.open(QuarkusPostgreSqlResource.TENANT_ID)) {
      assertTrue(actorService.count() >= 0L);
      assertThrows(
          jakarta.transaction.TransactionalException.class,
          () -> systemLocks.acquireLock("contract-lock"));
    }
  }

  @Test
  void tenantConnectionUsesTheFixedFunctionalSchemaNotATenantDerivedOne() throws Exception {
    var provider = tenantConnections.resolve(QuarkusPostgreSqlResource.TENANT_ID.toString());
    var tenantConnection = provider.getConnection();
    try {
      assertEquals(QuarkusPostgreSqlResource.SCHEMA.schemaName(), tenantConnection.getSchema());
    } finally {
      provider.closeConnection(tenantConnection);
    }
  }

  @Test
  void cachedProviderRejectsNewConnectionsAfterDeactivation() throws Exception {
    var provider = tenantConnections.resolve(QuarkusPostgreSqlResource.TENANT_ID.toString());
    provider.closeConnection(provider.getConnection());
    try {
      setTenantStatus("DEPROVISIONING");
      var rejection =
          assertThrows(
              io.quarkus.narayana.jta.QuarkusTransactionException.class, provider::getConnection);
      org.junit.jupiter.api.Assertions.assertInstanceOf(
          IllegalStateException.class, rejection.getCause());
      setTenantStatus("ACTIVE");
      provider.closeConnection(provider.getConnection());
    } finally {
      setTenantStatus("ACTIVE");
    }
  }

  private void setTenantStatus(String status) throws Exception {
    try (var connection = dataSource.getConnection();
        var update =
            connection.prepareStatement(
                "UPDATE tenant_registry SET status = ? WHERE tenant_id = ?")) {
      update.setString(1, status);
      update.setObject(2, QuarkusPostgreSqlResource.TENANT_ID.value());
      assertEquals(1, update.executeUpdate());
    }
  }

  @Test
  void frameworkManagedDataSourceRemainsUsableForBootstrapPurposes() throws Exception {
    try (var connection = dataSource.getConnection()) {
      assertTrue(connection.isValid(2));
    }
  }
}
