package com.forwardmeasure.jpa.asynctask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.jpa.asynctask.entity.AsyncTask;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskStatus;
import com.forwardmeasure.jpa.asynctask.repository.AsyncTaskRepository;
import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.asynctask.service.impl.AsyncTaskServiceImpl;
import com.forwardmeasure.jpa.asynctask.support.TestAsyncTaskType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskStatusHandlerTest {

    @Test
    void projectsFailureDetailsAndNormalizesRootApiPath() {
        AsyncTask task = task();
        task.setMaxAttempts(1);
        task.markProcessing();
        task.markFailed(
                "PROCESSOR_FAILED",
                "Processor failed",
                Map.of("step", "extract"));
        TaskStatusHandler handler = handler();

        Map<String, Object> projection = handler.buildStatusMap(task, "/");

        assertEquals("failed", projection.get("status"));
        assertEquals(
                "/tasks/" + task.getUuid() + "/status",
                projection.get("location"));
        assertEquals(
                Map.of(
                        "error_code", "PROCESSOR_FAILED",
                        "error_message", "Processor failed",
                        "error_detail", Map.of("step", "extract")),
                projection.get("error"));
        assertTrue(handler.isFailed(task));
        assertFalse(handler.isPending(task));
    }

    @Test
    void projectsCancelledTaskWithoutError() {
        AsyncTask task = task();
        task.markCancelled();
        TaskStatusHandler handler = handler();

        Map<String, Object> projection = handler.buildStatusMap(task, null);

        assertEquals("cancelled", projection.get("status"));
        assertNull(projection.get("error"));
        assertTrue(handler.isCancelled(task));
    }

    @Test
    void returnsEmptyResultForExternalOrAbsentPayload() {
        AsyncTask external = task();
        external.markProcessing();
        external.markCompletedWithUri("object://tenant/result.json");
        TaskStatusHandler handler = handler();

        assertTrue(handler.deserializeResult(external, Result.class).isEmpty());

        AsyncTask empty = task();
        empty.markProcessing();
        empty.markCompleted(Map.of());
        assertTrue(handler.deserializeResult(empty, Result.class).isEmpty());
    }

    private TaskStatusHandler handler() {
        return new TaskStatusHandler(
                new AsyncTaskServiceImpl(new AsyncTaskRepository()),
                new ObjectMapper());
    }

    private AsyncTask task() {
        OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);
        return AsyncTask.builder()
                .uuid(UUID.randomUUID())
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .taskType(TestAsyncTaskType.EXTRACTION)
                .resourceType("evidence")
                .taskResourceId(UUID.randomUUID())
                .status(AsyncTaskStatus.ACCEPTED)
                .maxAttempts(3)
                .expiresAt(timestamp.plusDays(1))
                .build();
    }

    private record Result(int count) {
    }
}
