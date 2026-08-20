package com.forwardmeasure.jpa.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity;
import com.forwardmeasure.jpa.contract.repository.ContractOwnedEntityRepository;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import jakarta.persistence.OptimisticLockException;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

@WithPostgreSqlContainer(databaseName = "jpa_persistence_contract")
class JpaPersistenceContractTest {

  @Test
  void mappingMatchesMigrationsAndEnforcesOptimisticLocking(PostgreSqlTestContainer database) {
    TenantSchema schema = schema();
    database.createSchema(schema.value());
    migrate(database, schema);

    try (SessionFactory sessions = sessions(database, schema)) {
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

  @Test
  void tenantSchemasHaveIndependentIdentityAndOwnedData(PostgreSqlTestContainer database) {
    TenantSchema first = schema();
    TenantSchema second = schema();
    database.createSchema(first.value());
    database.createSchema(second.value());
    migrate(database, first);
    migrate(database, second);

    try (SessionFactory firstSessions = sessions(database, first);
        SessionFactory secondSessions = sessions(database, second)) {
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

  private void migrate(PostgreSqlTestContainer database, TenantSchema schema) {
    new TenantSchemaMigrator(
            database.dataSource(),
            "db/changelog/forwardmeasure-jpa-contract-tests.xml",
            getClass().getClassLoader())
        .migrate(schema);
  }

  private SessionFactory sessions(PostgreSqlTestContainer database, TenantSchema schema) {
    Configuration configuration = new Configuration();
    configuration.addAnnotatedClass(Actor.class);
    configuration.addAnnotatedClass(ContractOwnedEntity.class);
    Map<String, String> properties =
        Map.of(
            "jakarta.persistence.jdbc.url", database.hostJdbcUrl(),
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
}
