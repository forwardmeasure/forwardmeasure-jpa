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
package com.forwardmeasure.jpa.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity;
import com.forwardmeasure.jpa.contract.repository.ContractOwnedEntityRepository;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.liquibase.TenantIdentityMigrator;
import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import jakarta.persistence.OptimisticLockException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

@WithPostgreSqlContainer(databaseName = "jpa_persistence_contract")
class JpaPersistenceContractTest {

  @Test
  void mappingMatchesMigrationsAndEnforcesOptimisticLocking(PostgreSqlTestContainer database) {
    TenantSchema schema = schema();
    database.createSchema(schema.value());
    TenantDatabase tenant = new TenantDatabase(database.hostJdbcUrl(), database.dataSource());
    migrate(tenant, schema);

    try (SessionFactory sessions = sessions(database, tenant, schema)) {
      ContractResult created;
      try (var session = sessions.openSession()) {
        var transaction = session.beginTransaction();
        created = verify(session);
        transaction.commit();
      }

      try (var first = sessions.openSession();
          var second = sessions.openSession()) {
        var firstTransaction = first.beginTransaction();
        var secondTransaction = second.beginTransaction();
        ContractOwnedEntity firstCopy = first.find(ContractOwnedEntity.class, created.entityId());
        ContractOwnedEntity secondCopy = second.find(ContractOwnedEntity.class, created.entityId());
        firstCopy.setName("first-writer");
        firstTransaction.commit();
        secondCopy.setName("stale-writer");
        assertThrows(OptimisticLockException.class, secondTransaction::commit);
      }
    }
  }

  /**
   * Tenants are isolated by database: each tenant database has its own identity schema and its own
   * product schemas, so neither actors nor owned data cross between tenants. (Within one tenant
   * database, actors are deliberately shared by every product schema.)
   */
  @Test
  void tenantDatabasesHaveIndependentIdentityAndOwnedData(PostgreSqlTestContainer database)
      throws SQLException {
    TenantDatabase firstTenant = createDatabase(database);
    TenantDatabase secondTenant = createDatabase(database);
    TenantSchema schema = schema();
    for (TenantDatabase tenant : new TenantDatabase[] {firstTenant, secondTenant}) {
      createSchema(tenant, schema);
      migrate(tenant, schema);
    }

    try (SessionFactory firstSessions = sessions(database, firstTenant, schema);
        SessionFactory secondSessions = sessions(database, secondTenant, schema)) {
      persistContract(firstSessions);

      assertEquals(1L, count(firstSessions, Actor.class));
      assertEquals(0L, count(secondSessions, Actor.class));
      assertEquals(1L, count(firstSessions, ContractOwnedEntity.class));
      assertEquals(0L, count(secondSessions, ContractOwnedEntity.class));
    }
  }

  private void persistContract(SessionFactory sessions) {
    try (var session = sessions.openSession()) {
      var transaction = session.beginTransaction();
      verify(session);
      transaction.commit();
    }
  }

  private long count(SessionFactory sessions, Class<?> entityType) {
    try (var session = sessions.openSession()) {
      var builder = session.getCriteriaBuilder();
      var query = builder.createQuery(Long.class);
      var root = query.from(entityType);
      query.select(builder.count(root));
      return session.createQuery(query).getSingleResult();
    }
  }

  private ContractResult verify(org.hibernate.Session session) {
    ActorRepository actors = new ActorRepository();
    actors.bindPersistenceContext(session);
    ContractOwnedEntityRepository owned = new ContractOwnedEntityRepository();
    owned.bindPersistenceContext(session);
    return JpaPersistenceContract.verify(actors, owned);
  }

  private void migrate(TenantDatabase tenant, TenantSchema schema) {
    new TenantIdentityMigrator(tenant.dataSource(), getClass().getClassLoader()).migrate();
    new TenantSchemaMigrator(
            tenant.dataSource(),
            "db/changelog/forwardmeasure-jpa-contract-tests.xml",
            getClass().getClassLoader())
        .migrate(schema);
  }

  private SessionFactory sessions(
      PostgreSqlTestContainer database, TenantDatabase tenant, TenantSchema schema) {
    Configuration configuration = new Configuration();
    configuration.addAnnotatedClass(Actor.class);
    configuration.addAnnotatedClass(ContractOwnedEntity.class);
    Map<String, String> properties =
        Map.of(
            "jakarta.persistence.jdbc.url", tenant.jdbcUrl(),
            "jakarta.persistence.jdbc.user", database.username(),
            "jakarta.persistence.jdbc.password", database.password(),
            "jakarta.persistence.jdbc.driver", "org.postgresql.Driver",
            "hibernate.default_schema", schema.value(),
            "hibernate.hbm2ddl.auto", "validate",
            "hibernate.show_sql", "false");
    properties.forEach(configuration::setProperty);
    return configuration.buildSessionFactory();
  }

  private TenantSchema schema() {
    return TenantSchema.forTenant(new TenantId(UUID.randomUUID()));
  }

  /** A fresh database standing in for one tenant's own database, on the shared container. */
  private TenantDatabase createDatabase(PostgreSqlTestContainer database) throws SQLException {
    String name = "tenant_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection connection = database.dataSource().getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("create database " + name);
    }
    String url = database.hostJdbcUrl().replace("/" + database.databaseName(), "/" + name);
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(url);
    dataSource.setUser(database.username());
    dataSource.setPassword(database.password());
    return new TenantDatabase(url, dataSource);
  }

  private static void createSchema(TenantDatabase tenant, TenantSchema schema) throws SQLException {
    try (Connection connection = tenant.dataSource().getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("create schema \"" + schema.value() + "\"");
    }
  }

  private record TenantDatabase(String jdbcUrl, DataSource dataSource) {}
}
