package com.forwardmeasure.jpa.asynctask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.jpa.asynctask.entity.AsyncTask;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskStatus;
import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.asynctask.support.AsyncTaskJpaFixture;
import com.forwardmeasure.jpa.asynctask.support.TestAsyncTaskType;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import jakarta.persistence.PersistenceException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

@WithPostgreSqlContainer(databaseName = "jpa_async_task_detailed_contract")
class AsyncTaskDetailedPostgreSqlTest {

    @Test
    void createsTaskWithTypeDefaultsAndNormalizedIdempotency(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            AsyncTask created = fixture.transaction(context -> {
                AsyncTask task = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()),
                        "  ",
                        "sha256:request");
                return task;
            });

            assertNotNull(created.getId());
            assertNotNull(created.getUuid());
            assertEquals(AsyncTaskStatus.ACCEPTED, created.getStatus());
            assertEquals("evidence", created.getResourceType());
            assertEquals(3, created.getMaxAttempts());
            assertNull(created.getIdempotencyKey());
            assertEquals(
                    "sha256:request",
                    created.getIdempotencyFingerprint());
            assertTrue(created.getExpiresAt().isAfter(created.getCreatedAt()));
        }
    }

    @Test
    void findsTasksByIdempotencyKeyAndFingerprint(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            fixture.transaction(context -> {
                AsyncTask task = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()),
                        "request-key",
                        "request-fingerprint");
                assertEquals(
                        task.getUuid(),
                        context.service().findByIdempotencyKey("request-key")
                                .orElseThrow()
                                .getUuid());
                assertEquals(
                        task.getUuid(),
                        context.service().findByIdempotencyFingerprint(
                                        "request-fingerprint")
                                .orElseThrow()
                                .getUuid());
                assertTrue(context.service().findByIdempotencyKey(" ")
                        .isEmpty());
                assertTrue(context.service().findByIdempotencyFingerprint(null)
                        .isEmpty());
                return null;
            });
        }
    }

    @Test
    void enforcesDatabaseIdempotencyUniqueness(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            UUID firstResource = UUID.randomUUID();
            fixture.transaction(context -> {
                context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        firstResource,
                        fixture.actor(context.actors()),
                        "duplicate-key",
                        "duplicate-fingerprint");
                return null;
            });

            assertThrows(PersistenceException.class, () ->
                    fixture.transaction(context -> {
                        context.service().createTask(
                                TestAsyncTaskType.EXTRACTION,
                                UUID.randomUUID(),
                                fixture.actor(context.actors()),
                                "duplicate-key",
                                "different-fingerprint");
                        return null;
                    }));
            assertThrows(PersistenceException.class, () ->
                    fixture.transaction(context -> {
                        context.service().createTask(
                                TestAsyncTaskType.EXTRACTION,
                                UUID.randomUUID(),
                                fixture.actor(context.actors()),
                                "different-key",
                                "duplicate-fingerprint");
                        return null;
                    }));
        }
    }

    @Test
    void concurrentIdempotentInsertAllowsExactlyOneTask(
            PostgreSqlTestContainer database) throws Exception {
        try (var fixture = AsyncTaskJpaFixture.create(database);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> concurrentCreate(
                    fixture, ready, start, "concurrent-key"));
            var second = executor.submit(() -> concurrentCreate(
                    fixture, ready, start, "concurrent-key"));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            RuntimeException firstResult = first.get(10, TimeUnit.SECONDS);
            RuntimeException secondResult = second.get(10, TimeUnit.SECONDS);
            List<RuntimeException> failures = java.util.stream.Stream.of(
                            firstResult,
                            secondResult)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            assertEquals(1, failures.size());
            assertInstanceOf(PersistenceException.class, failures.getFirst());
            fixture.transaction(context -> {
                assertEquals(1L, context.service().countTasks(
                        null,
                        TestAsyncTaskType.EXTRACTION.value(),
                        "evidence"));
                return null;
            });
        }
    }

    @Test
    void transactionRollbackRemovesTaskAndActorWrites(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            UUID resourceId = UUID.randomUUID();
            assertThrows(DeliberateRollback.class, () ->
                    fixture.transaction(context -> {
                        context.service().createTask(
                                TestAsyncTaskType.EXTRACTION,
                                resourceId,
                                fixture.actor(context.actors()));
                        throw new DeliberateRollback();
                    }));

            fixture.transaction(context -> {
                assertEquals(0L, context.service().countByResourceId(
                        resourceId, null));
                assertEquals(0L, context.actors().count());
                return null;
            });
        }
    }

    @Test
    void queriesByResourceAndStatus(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            UUID resourceId = UUID.randomUUID();
            fixture.transaction(context -> {
                AsyncTask accepted = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        resourceId,
                        fixture.actor(context.actors()));
                AsyncTask processing = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        resourceId,
                        fixture.actor(context.actors()));
                context.service().markProcessing(processing.getUuid());
                context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));

                assertEquals(2, context.service().findByResource(
                        "evidence", resourceId).size());
                assertEquals(List.of(accepted.getUuid()),
                        context.service().findByResourceAndStatus(
                                        "evidence",
                                        resourceId,
                                        AsyncTaskStatus.ACCEPTED)
                                .stream()
                                .map(AsyncTask::getUuid)
                                .toList());
                assertEquals(1, context.service().findByResourceAndStatus(
                        "evidence",
                        resourceId,
                        AsyncTaskStatus.PROCESSING).size());
                return null;
            });
        }
    }

    @Test
    void filtersCountsAndPagesTaskListings(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            UUID extractionResource = UUID.randomUUID();
            UUID screeningResource = UUID.randomUUID();
            fixture.transaction(context -> {
                context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        extractionResource,
                        fixture.actor(context.actors()));
                context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        extractionResource,
                        fixture.actor(context.actors()));
                context.service().createTask(
                        TestAsyncTaskType.SCREENING,
                        screeningResource,
                        fixture.actor(context.actors()));

                assertEquals(2L, context.service().countTasks(
                        AsyncTaskStatus.ACCEPTED,
                        TestAsyncTaskType.EXTRACTION.value(),
                        "evidence"));
                assertEquals(1, context.service().listTasks(
                        AsyncTaskStatus.ACCEPTED,
                        TestAsyncTaskType.EXTRACTION.value(),
                        "evidence",
                        0,
                        1).size());
                assertEquals(1, context.service().listTasks(
                        AsyncTaskStatus.ACCEPTED,
                        TestAsyncTaskType.EXTRACTION.value(),
                        "evidence",
                        1,
                        1).size());
                assertEquals(2L, context.service().countByResourceId(
                        extractionResource, null));
                assertEquals(2L, context.service().countByResourceId(
                        extractionResource, AsyncTaskStatus.ACCEPTED));
                assertEquals(2, context.service().listByResourceId(
                        extractionResource,
                        AsyncTaskStatus.ACCEPTED,
                        0,
                        10).size());
                return null;
            });
        }
    }

    @Test
    void rejectsInvalidQueryPaginationAndTaskType(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            fixture.transaction(context -> {
                assertThrows(IllegalArgumentException.class, () ->
                        context.service().listTasks(
                                null, null, null, -1, 10));
                assertThrows(IllegalArgumentException.class, () ->
                        context.service().listTasks(
                                null, null, null, 0, 0));
                assertThrows(IllegalArgumentException.class, () ->
                        context.service().findRetryable(" ", 10));
                assertThrows(IllegalArgumentException.class, () ->
                        context.service().findDispatchableRetries(0));
                return null;
            });
        }
    }

    @Test
    void selectsOnlyDueRetryableAndDispatchableTasks(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            fixture.transaction(context -> {
                AsyncTask due = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                due.setDispatchTopicPath("tasks.dispatch");
                due.setDispatchEventType("task.requested");
                context.service().markProcessing(due.getUuid());
                context.service().markFailed(
                        due.getUuid(),
                        "RETRY",
                        "retry",
                        Map.of(),
                        Duration.ZERO);

                AsyncTask future = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                future.setDispatchTopicPath("tasks.dispatch");
                future.setDispatchEventType("task.requested");
                context.service().markProcessing(future.getUuid());
                context.service().markFailed(
                        future.getUuid(),
                        "RETRY",
                        "retry later",
                        Map.of(),
                        Duration.ofDays(1));

                AsyncTask undispatchable = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                context.service().markProcessing(undispatchable.getUuid());
                context.service().markFailed(
                        undispatchable.getUuid(),
                        "RETRY",
                        "no dispatch metadata",
                        Map.of(),
                        Duration.ZERO);

                assertEquals(
                        Set.of(due.getUuid(), undispatchable.getUuid()),
                        context.service().findRetryable(
                                        TestAsyncTaskType.EXTRACTION.value(),
                                        10)
                                .stream()
                                .map(AsyncTask::getUuid)
                                .collect(java.util.stream.Collectors.toSet()));
                assertEquals(List.of(due.getUuid()),
                        context.service().findDispatchableRetries(10)
                                .stream()
                                .map(AsyncTask::getUuid)
                                .toList());
                return null;
            });
        }
    }

    @Test
    void findsExpiredProcessingLeasesAndRespectsLimit(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            fixture.transaction(context -> {
                AsyncTask expired = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                context.service().markProcessing(
                        expired.getUuid(),
                        "expired-worker",
                        now().minusMinutes(1));

                AsyncTask missingLease = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                context.service().markProcessing(missingLease.getUuid());

                AsyncTask active = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                context.service().markProcessing(
                        active.getUuid(),
                        "active-worker",
                        now().plusMinutes(5));

                assertEquals(2,
                        context.service().findExpiredProcessingLeases(10)
                                .size());
                assertEquals(1,
                        context.service().findExpiredProcessingLeases(1)
                                .size());
                return null;
            });
        }
    }

    @Test
    void extendsLeaseOnlyForCurrentOwnerAndExistingTask(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            fixture.transaction(context -> {
                AsyncTask task = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                context.service().markProcessing(
                        task.getUuid(),
                        "worker-1",
                        now().plusMinutes(1));
                assertFalse(context.service().extendProcessingLease(
                        task.getUuid(),
                        "worker-2",
                        now().plusMinutes(2)));
                assertTrue(context.service().extendProcessingLease(
                        task.getUuid(),
                        "worker-1",
                        now().plusMinutes(3)));
                assertFalse(context.service().extendProcessingLease(
                        UUID.randomUUID(),
                        "worker-1",
                        now().plusMinutes(3)));
                return null;
            });
        }
    }

    @Test
    void persistsDeferredCompletionAndStatusProjection(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            UUID taskId = fixture.transaction(context -> {
                AsyncTask task = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                context.service().markProcessing(task.getUuid());
                context.service().deferCompletion(
                        task.getUuid(),
                        Map.of("work_units_expected", 3L));
                return task.getUuid();
            });

            fixture.transaction(context -> {
                AsyncTask task = context.service().findByUuid(taskId)
                        .orElseThrow();
                assertEquals("AWAITING_DOWNSTREAM_PROCESSING",
                        task.getProgressPayload().get("phase"));
                TaskStatusHandler handler = new TaskStatusHandler(
                        context.service(), new ObjectMapper());
                Map<String, Object> status = handler.buildStatusMap(
                        task, "api/v1/");
                assertEquals("processing", status.get("status"));
                assertEquals(
                        "/api/v1/tasks/" + taskId + "/status",
                        status.get("location"));
                assertTrue(handler.isPending(task));
                return null;
            });
        }
    }

    @Test
    void deserializesInlineResultAndReturnsEmptyForInvalidShape(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            fixture.transaction(context -> {
                AsyncTask task = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                context.service().markProcessing(task.getUuid());
                context.service().markCompleted(
                        task.getUuid(), Map.of("count", 7));
                TaskStatusHandler handler = new TaskStatusHandler(
                        context.service(), new ObjectMapper());
                assertEquals(new Result(7), handler.deserializeResult(
                        task, Result.class).orElseThrow());
                assertTrue(handler.deserializeResult(task, UUID.class)
                        .isEmpty());
                assertTrue(handler.isCompleted(task));
                return null;
            });
        }
    }

    @Test
    void cancelsOnlyActiveTasksForRequestedResource(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            UUID resourceId = UUID.randomUUID();
            fixture.transaction(context -> {
                AsyncTask accepted = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        resourceId,
                        fixture.actor(context.actors()));
                AsyncTask processing = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        resourceId,
                        fixture.actor(context.actors()));
                context.service().markProcessing(processing.getUuid());
                AsyncTask completed = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        resourceId,
                        fixture.actor(context.actors()));
                context.service().markProcessing(completed.getUuid());
                context.service().markCompleted(completed.getUuid(), Map.of());

                assertEquals(2, context.service().cancelByResource(
                        "evidence", resourceId));
                assertTrue(context.service().findByUuid(accepted.getUuid())
                        .orElseThrow().isCancelled());
                assertTrue(context.service().findByUuid(processing.getUuid())
                        .orElseThrow().isCancelled());
                assertTrue(context.service().findByUuid(completed.getUuid())
                        .orElseThrow().isCompleted());
                return null;
            });
        }
    }

    @Test
    void marksIndividualTaskCancelledAndReturnsEmptyWhenMissing(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            fixture.transaction(context -> {
                AsyncTask task = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                assertTrue(context.service().markCancelled(task.getUuid())
                        .orElseThrow().isCancelled());
                assertTrue(context.service().markCancelled(UUID.randomUUID())
                        .isEmpty());
                assertTrue(context.service().markProcessing(UUID.randomUUID())
                        .isEmpty());
                return null;
            });
        }
    }

    @Test
    void deletesAllTasksForResource(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            UUID deletedResource = UUID.randomUUID();
            UUID retainedResource = UUID.randomUUID();
            fixture.transaction(context -> {
                context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        deletedResource,
                        fixture.actor(context.actors()));
                context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        deletedResource,
                        fixture.actor(context.actors()));
                context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        retainedResource,
                        fixture.actor(context.actors()));
                assertEquals(2L, context.service().deleteByResource(
                        "evidence", deletedResource));
                assertEquals(0L, context.service().countByResourceId(
                        deletedResource, null));
                assertEquals(1L, context.service().countByResourceId(
                        retainedResource, null));
                return null;
            });
        }
    }

    @Test
    void deletesOnlyExpiredTerminalTasks(
            PostgreSqlTestContainer database) {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            fixture.transaction(context -> {
                AsyncTask expired = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                context.service().markProcessing(expired.getUuid());
                context.service().markCompleted(expired.getUuid(), Map.of());

                AsyncTask future = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));
                context.service().markProcessing(future.getUuid());
                context.service().markCompleted(future.getUuid(), Map.of());

                AsyncTask active = context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()));

                context.repository().flush();
                context.entityManager()
                        .createQuery(
                                "update AsyncTask task "
                                        + "set task.createdAt = :created, "
                                        + "task.expiresAt = :expired "
                                        + "where task.uuid = :uuid")
                        .setParameter("created", now().minusDays(2))
                        .setParameter("expired", now().minusDays(1))
                        .setParameter("uuid", expired.getUuid())
                        .executeUpdate();
                context.entityManager().clear();

                assertEquals(1L, context.service().deleteExpired());
                assertTrue(context.service().findByUuid(expired.getUuid())
                        .isEmpty());
                assertTrue(context.service().findByUuid(future.getUuid())
                        .isPresent());
                assertTrue(context.service().findByUuid(active.getUuid())
                        .isPresent());
                return null;
            });
        }
    }

    @Test
    void pessimisticUpdateLockSerializesCompetingWorkers(
            PostgreSqlTestContainer database) throws Exception {
        try (var fixture = AsyncTaskJpaFixture.create(database)) {
            UUID taskId = fixture.transaction(context -> context.service()
                    .createTask(
                            TestAsyncTaskType.EXTRACTION,
                            UUID.randomUUID(),
                            fixture.actor(context.actors()))
                    .getUuid());

            try (var firstEntityManager = fixture.newEntityManager();
                    var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var firstTransaction = firstEntityManager.getTransaction();
                firstTransaction.begin();
                var firstContext = fixture.context(firstEntityManager);
                firstContext.service().markProcessing(
                        taskId,
                        "worker-1",
                        now().plusMinutes(1));

                CountDownLatch contenderStarted = new CountDownLatch(1);
                var contender = executor.submit(() -> {
                    contenderStarted.countDown();
                    try {
                        fixture.transaction(context -> {
                            context.service().markProcessing(
                                    taskId,
                                    "worker-2",
                                    now().plusMinutes(1));
                            return null;
                        });
                        return null;
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                });
                assertTrue(contenderStarted.await(5, TimeUnit.SECONDS));
                assertThrows(
                        TimeoutException.class,
                        () -> contender.get(250, TimeUnit.MILLISECONDS));

                firstTransaction.commit();
                RuntimeException failure = contender.get(5, TimeUnit.SECONDS);
                assertInstanceOf(IllegalStateException.class, failure);
            }

            fixture.transaction(context -> {
                AsyncTask task = context.service().findByUuid(taskId)
                        .orElseThrow();
                assertEquals(AsyncTaskStatus.PROCESSING, task.getStatus());
                assertEquals("worker-1", task.getProcessingOwner());
                assertEquals(1, task.getAttemptCount());
                return null;
            });
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private RuntimeException concurrentCreate(
            AsyncTaskJpaFixture fixture,
            CountDownLatch ready,
            CountDownLatch start,
            String idempotencyKey) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new IllegalStateException(
                        "Timed out waiting to start concurrent insert");
            }
            fixture.transaction(context -> {
                context.service().createTask(
                        TestAsyncTaskType.EXTRACTION,
                        UUID.randomUUID(),
                        fixture.actor(context.actors()),
                        idempotencyKey,
                        null);
                return null;
            });
            return null;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return new IllegalStateException(failure);
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private static final class DeliberateRollback extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    private record Result(int count) {
    }
}
