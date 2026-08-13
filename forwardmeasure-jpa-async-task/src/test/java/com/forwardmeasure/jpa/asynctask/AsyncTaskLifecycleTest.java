package com.forwardmeasure.jpa.asynctask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.asynctask.entity.AsyncTask;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AsyncTaskLifecycleTest {

    @Test
    void startsProcessingAndRecordsFirstAttemptAndLease() {
        AsyncTask task = task(3);
        OffsetDateTime lease = now().plusMinutes(2);

        task.markProcessing("worker-1", lease);

        assertEquals(AsyncTaskStatus.PROCESSING, task.getStatus());
        assertEquals(1, task.getAttemptCount());
        assertEquals("worker-1", task.getProcessingOwner());
        assertEquals(lease, task.getProcessingLeaseExpiresAt());
        assertNotNull(task.getStartedAt());
        assertNull(task.getNextRetryAt());
    }

    @Test
    void refusesProcessingAfterMaximumAttemptsAreExhausted() {
        AsyncTask task = task(1);
        task.setAttemptCount(1);

        assertThrows(IllegalStateException.class, task::markProcessing);
    }

    @Test
    void retriesFailureUntilMaximumAttemptsThenFailsTerminally() {
        AsyncTask task = task(2);
        task.markProcessing();
        task.markFailed(
                "TEMPORARY",
                "Retry",
                Map.of("retryable", true),
                Duration.ZERO);

        assertEquals(AsyncTaskStatus.ACCEPTED, task.getStatus());
        assertTrue(task.isRetryable());
        assertNotNull(task.getNextRetryAt());
        assertNull(task.getProcessingOwner());

        task.markProcessing();
        task.markFailed("TERMINAL", "Failed", Map.of("reason", "fatal"));

        assertEquals(AsyncTaskStatus.FAILED, task.getStatus());
        assertTrue(task.isTerminal());
        assertFalse(task.isRetryable());
        assertNotNull(task.getCompletedAt());
        assertEquals("TERMINAL", task.getErrorCode());
        assertEquals("Failed", task.getErrorMessage());
        assertEquals(Map.of("reason", "fatal"), task.getErrorDetail());
    }

    @Test
    void computesDefaultRetryDelayAndRejectsNegativeDelay() {
        AsyncTask task = task(3);
        task.markProcessing();
        OffsetDateTime before = now().plusSeconds(29);
        task.markFailed("TEMPORARY", "retry", Map.of());
        OffsetDateTime after = now().plusSeconds(31);

        assertTrue(task.getNextRetryAt().isAfter(before));
        assertTrue(task.getNextRetryAt().isBefore(after));

        task.markProcessing();
        assertThrows(
                IllegalArgumentException.class,
                () -> task.markFailed(
                        "INVALID",
                        "negative",
                        Map.of(),
                        Duration.ofSeconds(-1)));
    }

    @Test
    void mergesProgressAndDefensivelyCopiesPayloads() {
        AsyncTask task = task(2);
        task.markProcessing();
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("records", 2);
        task.markProgress(first);
        first.put("records", 99);
        task.markProgress(Map.of("pages", 3));

        assertEquals(Map.of("records", 2, "pages", 3),
                task.getProgressPayload());

        task.markProgress(Map.of());
        assertNull(task.getProgressPayload());
    }

    @Test
    void defersCompletionToDownstreamProcessing() {
        AsyncTask task = task(2);
        task.markProcessing("worker", now().plusMinutes(1));

        task.deferCompletion(Map.of("work_units_expected", 2L));

        assertEquals(AsyncTaskStatus.PROCESSING, task.getStatus());
        assertEquals("AWAITING_DOWNSTREAM_PROCESSING",
                task.getProgressPayload().get("phase"));
        assertEquals(0L,
                task.getProgressPayload().get("work_units_completed"));
        assertEquals("downstream_completion", task.getProcessingOwner());
        assertEquals(task.getExpiresAt(), task.getProcessingLeaseExpiresAt());
    }

    @Test
    void completesDeferredTaskWhenExpectedWorkIsAlreadyComplete() {
        AsyncTask task = task(2);
        task.markProcessing();

        task.deferCompletion(Map.of(
                "work_units_expected", 2L,
                "work_units_completed", 2L));

        assertEquals(AsyncTaskStatus.COMPLETED, task.getStatus());
        assertEquals("COMPLETED", task.getResultPayload().get("phase"));
        assertNull(task.getProcessingOwner());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void completesWithExactlyOneDefensivelyCopiedResultRepresentation() {
        AsyncTask inline = task(1);
        inline.markProcessing();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("answer", 42);
        inline.markCompleted(payload);
        payload.put("answer", 99);

        assertEquals(Map.of("answer", 42), inline.getResultPayload());
        assertNull(inline.getResultUri());

        AsyncTask external = task(1);
        external.markProcessing();
        external.markCompletedWithUri("object://tenant/results/task-1.json");
        assertEquals(
                "object://tenant/results/task-1.json",
                external.getResultUri());
        assertNull(external.getResultPayload());
    }

    @Test
    void rejectsBlankExternalResultUri() {
        AsyncTask task = task(1);
        task.markProcessing();

        assertThrows(
                IllegalArgumentException.class,
                () -> task.markCompletedWithUri("  "));
    }

    @Test
    void cancelsAcceptedOrProcessingTaskAndClearsLease() {
        AsyncTask accepted = task(1);
        accepted.markCancelled();
        assertTrue(accepted.isCancelled());
        assertNotNull(accepted.getCompletedAt());

        AsyncTask processing = task(1);
        processing.markProcessing("worker", now().plusMinutes(1));
        processing.markCancelled();
        assertTrue(processing.isCancelled());
        assertNull(processing.getProcessingOwner());
        assertNull(processing.getProcessingLeaseExpiresAt());
    }

    @Test
    void skipsActiveTaskAndMakesItTerminal() {
        AsyncTask task = task(1);

        task.markSkipped();

        assertEquals(AsyncTaskStatus.SKIPPED, task.getStatus());
        assertTrue(task.isTerminal());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void evaluatesAndExtendsLeaseOnlyForOwningWorker() {
        AsyncTask task = task(2);
        OffsetDateTime originalLease = now().minusSeconds(1);
        task.markProcessing("worker-1", originalLease);

        assertTrue(task.processingLeaseExpired(now()));
        assertFalse(task.extendProcessingLease(
                "worker-2", now().plusMinutes(1)));
        OffsetDateTime extended = now().plusMinutes(2);
        assertTrue(task.extendProcessingLease("worker-1", extended));
        assertEquals(extended, task.getProcessingLeaseExpiresAt());
        assertFalse(task.processingLeaseExpired(now()));
    }

    @Test
    void treatsMissingProcessingLeaseAsExpired() {
        AsyncTask task = task(2);
        task.markProcessing();

        assertTrue(task.processingLeaseExpired(now()));
    }

    @Test
    void rejectsTransitionsFromTerminalState() {
        AsyncTask task = task(1);
        task.markProcessing();
        task.markCompleted(Map.of());

        assertThrows(IllegalStateException.class, task::markProcessing);
        assertThrows(IllegalStateException.class, task::markCancelled);
        assertThrows(IllegalStateException.class, task::markSkipped);
        assertThrows(IllegalStateException.class,
                () -> task.markProgress(Map.of("late", true)));
        assertThrows(IllegalStateException.class,
                () -> task.markFailed("LATE", "late", Map.of()));
    }

    private AsyncTask task(int maxAttempts) {
        return AsyncTask.builder()
                .status(AsyncTaskStatus.ACCEPTED)
                .maxAttempts(maxAttempts)
                .expiresAt(now().plusDays(1))
                .build();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
