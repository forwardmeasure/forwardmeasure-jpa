package com.forwardmeasure.jpa.asynctask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.jpa.asynctask.converter.AsyncTaskTypeConverter;
import com.forwardmeasure.jpa.asynctask.entity.AsyncTask;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskStatus;
import com.forwardmeasure.jpa.asynctask.repository.AsyncTaskRepository;
import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.asynctask.service.impl.AsyncTaskServiceImpl;
import com.forwardmeasure.jpa.asynctask.support.TestAsyncTaskType;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@WithPostgreSqlContainer(databaseName = "jpa_async_task_contract")
class AsyncTaskPostgreSqlIntegrationTest {

    @BeforeAll
    static void registerTaskType() {
        AsyncTaskTypeConverter.register(TestAsyncTaskType.values());
    }

    @Test
    void persistsAndQueriesCompleteLifecycle(
            PostgreSqlTestContainer database) {
        TenantSchema tenant = prepare(database);
        try (EntityManagerFactory entityManagers = entityManagers(
                database, tenant)) {
            UUID resourceId = UUID.randomUUID();
            UUID taskId = inTransaction(entityManagers, context -> {
                Actor actor = actor(context.actors());
                AsyncTask task = context.tasks().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        resourceId,
                        actor,
                        "request-1",
                        "sha256:request-1");
                assertNotNull(task.getUuid());
                assertEquals(AsyncTaskStatus.ACCEPTED, task.getStatus());
                assertTrue(context.tasks()
                        .findByIdempotencyKey("request-1")
                        .isPresent());
                return task.getUuid();
            });

            inTransaction(entityManagers, context -> {
                OffsetDateTime lease = OffsetDateTime.now(ZoneOffset.UTC)
                        .plusMinutes(2);
                AsyncTask processing = context.tasks().markProcessing(
                        taskId, "worker-1", lease).orElseThrow();
                assertEquals(1, processing.getAttemptCount());
                assertTrue(context.tasks().extendProcessingLease(
                        taskId,
                        "worker-1",
                        lease.plusMinutes(1)));
                assertFalse(context.tasks().extendProcessingLease(
                        taskId,
                        "worker-2",
                        lease.plusMinutes(1)));
                context.tasks().markProgress(
                        taskId, Map.of("records_processed", 17));
                return null;
            });

            inTransaction(entityManagers, context -> {
                AsyncTask completed = context.tasks().markCompleted(
                        taskId, Map.of("entity_count", 4)).orElseThrow();
                assertEquals(AsyncTaskStatus.COMPLETED, completed.getStatus());
                assertEquals(
                        4,
                        completed.getResultPayload().get("entity_count"));
                assertEquals(
                        1,
                        context.tasks().listByResourceId(
                                resourceId,
                                AsyncTaskStatus.COMPLETED,
                                0,
                                10).size());

                TaskStatusHandler status = new TaskStatusHandler(
                        context.tasks(), new ObjectMapper());
                Map<String, Object> projection = status.buildStatusMap(
                        completed, "/api/v1");
                assertEquals("completed", projection.get("status"));
                assertEquals(
                        "/api/v1/tasks/" + taskId + "/status",
                        projection.get("location"));
                return null;
            });
        }
    }

    @Test
    void persistsRetryCancellationAndObjectStorageResult(
            PostgreSqlTestContainer database) {
        TenantSchema tenant = prepare(database);
        try (EntityManagerFactory entityManagers = entityManagers(
                database, tenant)) {
            UUID retryId = inTransaction(entityManagers, context -> {
                AsyncTask task = context.tasks().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        actor(context.actors()));
                return task.getUuid();
            });

            inTransaction(entityManagers, context -> {
                context.tasks().markProcessing(retryId);
                context.tasks().markFailed(
                        retryId,
                        "TEMPORARY",
                        "retry",
                        Map.of(),
                        Duration.ZERO);
                assertEquals(
                        1,
                        context.tasks().findRetryable(
                                TestAsyncTaskType.EXTRACTION.value(), 10).size());
                return null;
            });

            inTransaction(entityManagers, context -> {
                context.tasks().markProcessing(retryId);
                AsyncTask completed = context.tasks()
                        .markCompletedWithUri(
                                retryId,
                                "object://tenant/tasks/" + retryId)
                        .orElseThrow();
                assertTrue(completed.isCompleted());
                assertTrue(completed.getResultUri().startsWith("object://"));

                UUID cancellableResource = UUID.randomUUID();
                context.tasks().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        cancellableResource,
                        actor(context.actors()));
                assertEquals(
                        1,
                        context.tasks().cancelByResource(
                                "evidence", cancellableResource));
                return null;
            });
        }
    }

    private TenantSchema prepare(PostgreSqlTestContainer database) {
        TenantSchema tenant = TenantSchema.forTenant(
                new TenantId(UUID.randomUUID()));
        database.createSchema(tenant.value());
        TenantSchemaMigrator migrator = new TenantSchemaMigrator(
                database.dataSource(),
                "db/changelog/forwardmeasure-jpa-async-task-test.xml",
                getClass().getClassLoader());
        assertTrue(migrator.validate(tenant).valid());
        migrator.migrate(tenant);
        assertTrue(migrator.status(tenant).current());
        return tenant;
    }

    private EntityManagerFactory entityManagers(
            PostgreSqlTestContainer database, TenantSchema tenant) {
        return Persistence.createEntityManagerFactory(
                "forwardmeasure-jpa-async-task-test",
                Map.of(
                        "jakarta.persistence.jdbc.url", database.hostJdbcUrl(),
                        "jakarta.persistence.jdbc.user", database.username(),
                        "jakarta.persistence.jdbc.password", database.password(),
                        "jakarta.persistence.jdbc.driver", "org.postgresql.Driver",
                        "hibernate.default_schema", tenant.value()));
    }

    private <T> T inTransaction(
            EntityManagerFactory entityManagers,
            Function<Context, T> work) {
        EntityManager entityManager = entityManagers.createEntityManager();
        var transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            ActorRepository actors = new ActorRepository();
            actors.bindPersistenceContext(entityManager);
            AsyncTaskRepository tasks = new AsyncTaskRepository();
            tasks.bindPersistenceContext(entityManager);
            T result = work.apply(new Context(
                    actors, new AsyncTaskServiceImpl(tasks)));
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

    private Actor actor(ActorRepository actors) {
        Actor actor = Actor.builder()
                .subjectIdentifier("actor-" + UUID.randomUUID())
                .identityProvider("test")
                .type(IdentityType.SERVICE)
                .build();
        actors.persist(actor);
        return actor;
    }

    private record Context(
            ActorRepository actors, AsyncTaskServiceImpl tasks) {
    }

}
