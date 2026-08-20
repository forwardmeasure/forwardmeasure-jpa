package com.forwardmeasure.jpa.asynctask.support;

import com.forwardmeasure.jpa.asynctask.converter.AsyncTaskTypeConverter;
import com.forwardmeasure.jpa.asynctask.repository.AsyncTaskRepository;
import com.forwardmeasure.jpa.asynctask.service.impl.AsyncTaskServiceImpl;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class AsyncTaskJpaFixture implements AutoCloseable {

  private final EntityManagerFactory entityManagers;

  private AsyncTaskJpaFixture(EntityManagerFactory entityManagers) {
    this.entityManagers = entityManagers;
  }

  public static AsyncTaskJpaFixture create(PostgreSqlTestContainer database) {
    AsyncTaskTypeConverter.register(TestAsyncTaskType.values());
    TenantSchema tenant = TenantSchema.forTenant(new TenantId(UUID.randomUUID()));
    database.createSchema(tenant.value());
    TenantSchemaMigrator migrator =
        new TenantSchemaMigrator(
            database.dataSource(),
            "db/changelog/forwardmeasure-jpa-async-task-test.xml",
            AsyncTaskJpaFixture.class.getClassLoader());
    if (!migrator.validate(tenant).valid()) {
      throw new IllegalStateException("Async-task changelog is invalid");
    }
    migrator.migrate(tenant);
    if (!migrator.status(tenant).current()) {
      throw new IllegalStateException("Async-task schema is not current after migration");
    }
    return new AsyncTaskJpaFixture(
        Persistence.createEntityManagerFactory(
            "forwardmeasure-jpa-async-task-test",
            Map.of(
                "jakarta.persistence.jdbc.url",
                database.hostJdbcUrl(),
                "jakarta.persistence.jdbc.user",
                database.username(),
                "jakarta.persistence.jdbc.password",
                database.password(),
                "jakarta.persistence.jdbc.driver",
                "org.postgresql.Driver",
                "hibernate.default_schema",
                tenant.value())));
  }

  public <T> T transaction(Function<Context, T> work) {
    EntityManager entityManager = newEntityManager();
    var transaction = entityManager.getTransaction();
    try {
      transaction.begin();
      T result = work.apply(context(entityManager));
      transaction.commit();
      return result;
    } catch (RuntimeException | Error failure) {
      if (transaction.isActive()) {
        transaction.rollback();
      }
      throw failure;
    } finally {
      entityManager.close();
    }
  }

  public EntityManager newEntityManager() {
    return entityManagers.createEntityManager();
  }

  public Context context(EntityManager entityManager) {
    ActorRepository actors = new ActorRepository();
    actors.bindPersistenceContext(entityManager);
    AsyncTaskRepository tasks = new AsyncTaskRepository();
    tasks.bindPersistenceContext(entityManager);
    return new Context(entityManager, actors, tasks, new AsyncTaskServiceImpl(tasks));
  }

  public Actor actor(ActorRepository actors) {
    Actor actor =
        Actor.builder()
            .subjectIdentifier("actor-" + UUID.randomUUID())
            .identityProvider("test")
            .type(IdentityType.SERVICE)
            .build();
    actors.persist(actor);
    return actor;
  }

  @Override
  public void close() {
    entityManagers.close();
  }

  public record Context(
      EntityManager entityManager,
      ActorRepository actors,
      AsyncTaskRepository repository,
      AsyncTaskServiceImpl service) {}
}
