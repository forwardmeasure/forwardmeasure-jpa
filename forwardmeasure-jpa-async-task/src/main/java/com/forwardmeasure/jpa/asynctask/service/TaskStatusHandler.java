package com.forwardmeasure.jpa.asynctask.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.jpa.asynctask.entity.AsyncTask;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Framework-neutral status projection and typed-result helper. */
@Singleton
public class TaskStatusHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskStatusHandler.class);

  private final AsyncTaskService taskService;

  private final ObjectMapper objectMapper;

  @Inject
  public TaskStatusHandler(AsyncTaskService taskService, ObjectMapper objectMapper) {
    this.taskService = Objects.requireNonNull(taskService, "taskService");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public Optional<AsyncTask> findTask(UUID taskId) {
    return taskService.findByUuid(taskId);
  }

  public boolean isPending(AsyncTask task) {
    return task.getStatus() == AsyncTaskStatus.ACCEPTED
        || task.getStatus() == AsyncTaskStatus.PROCESSING;
  }

  public boolean isCompleted(AsyncTask task) {
    return task.getStatus() == AsyncTaskStatus.COMPLETED;
  }

  public boolean isFailed(AsyncTask task) {
    return task.getStatus() == AsyncTaskStatus.FAILED;
  }

  public boolean isCancelled(AsyncTask task) {
    return task.getStatus() == AsyncTaskStatus.CANCELLED;
  }

  public Map<String, Object> buildStatusMap(AsyncTask task, String baseApiPath) {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("task_id", task.getUuid().toString());
    status.put("status", task.getStatus().apiValue());
    status.put("task_type", task.getTaskType().value());
    status.put("resource_type", valueOrEmpty(task.getResourceType()));
    status.put(
        "resource_id", task.getTaskResourceId() == null ? "" : task.getTaskResourceId().toString());
    status.put("location", location(task, baseApiPath));
    status.put("created_at", task.getCreatedAt().toString());
    status.put("updated_at", string(task.getUpdatedAt()));
    status.put("started_at", string(task.getStartedAt()));
    status.put("completed_at", string(task.getCompletedAt()));
    status.put("attempt_count", task.getAttemptCount());
    status.put("max_attempts", task.getMaxAttempts());
    status.put("next_retry_at", string(task.getNextRetryAt()));
    status.put("error", error(task));
    status.put("progress", task.getProgressPayload());
    return status;
  }

  public <T> Optional<T> deserializeResult(AsyncTask task, Class<T> targetType) {
    Map<String, Object> payload = task.getResultPayload();
    if (payload == null || payload.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.convertValue(payload, targetType));
    } catch (IllegalArgumentException failure) {
      LOGGER.error(
          "Failed to deserialize task {} result as {}",
          task.getUuid(),
          targetType.getName(),
          failure);
      return Optional.empty();
    }
  }

  private static String location(AsyncTask task, String baseApiPath) {
    String path = baseApiPath == null ? "" : baseApiPath.trim();
    if (!path.isEmpty() && !path.startsWith("/")) {
      path = "/" + path;
    }
    while (path.endsWith("/") && path.length() > 1) {
      path = path.substring(0, path.length() - 1);
    }
    if ("/".equals(path)) {
      path = "";
    }
    return path + "/tasks/" + task.getUuid() + "/status";
  }

  private static Map<String, Object> error(AsyncTask task) {
    if (task.getErrorCode() == null
        && task.getErrorMessage() == null
        && task.getErrorDetail() == null) {
      return null;
    }
    Map<String, Object> error = new LinkedHashMap<>();
    error.put("error_code", task.getErrorCode());
    error.put("error_message", task.getErrorMessage());
    error.put("error_detail", task.getErrorDetail());
    return error;
  }

  private static String string(Object value) {
    return value == null ? null : value.toString();
  }

  private static String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
