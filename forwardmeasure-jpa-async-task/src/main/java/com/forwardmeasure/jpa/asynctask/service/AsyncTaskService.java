package com.forwardmeasure.jpa.asynctask.service;

import com.forwardmeasure.jpa.asynctask.entity.AsyncTask;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskStatus;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskType;
import com.forwardmeasure.jpa.core.service.AuditedEntityService;
import com.forwardmeasure.jpa.identity.entity.Actor;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Application-facing asynchronous task lifecycle operations. */
public interface AsyncTaskService extends AuditedEntityService<AsyncTask, Long> {

  Optional<AsyncTask> findByUuidForUpdate(UUID uuid);

  List<AsyncTask> findByResource(String resourceType, UUID resourceId);

  List<AsyncTask> findByResourceAndStatus(
      String resourceType, UUID resourceId, AsyncTaskStatus status);

  List<AsyncTask> findRetryable(String taskType, int limit);

  List<AsyncTask> findDispatchableRetries(int limit);

  List<AsyncTask> findExpiredProcessingLeases(int limit);

  Optional<AsyncTask> findByIdempotencyKey(String key);

  Optional<AsyncTask> findByIdempotencyFingerprint(String fingerprint);

  List<AsyncTask> listTasks(
      AsyncTaskStatus status, String taskType, String resourceType, int page, int pageSize);

  long countTasks(AsyncTaskStatus status, String taskType, String resourceType);

  List<AsyncTask> listByResourceId(UUID resourceId, AsyncTaskStatus status, int page, int pageSize);

  long countByResourceId(UUID resourceId, AsyncTaskStatus status);

  AsyncTask createTask(AsyncTaskType taskType, UUID resourceId, Actor actor);

  AsyncTask createTask(AsyncTaskType taskType, UUID resourceId, Actor actor, String idempotencyKey);

  AsyncTask createTask(
      AsyncTaskType taskType,
      UUID resourceId,
      Actor actor,
      String idempotencyKey,
      String idempotencyFingerprint);

  int cancelByResource(String resourceType, UUID resourceId);

  long deleteByResource(String resourceType, UUID resourceId);

  long deleteExpired();

  Optional<AsyncTask> markProcessing(UUID taskId);

  Optional<AsyncTask> markProcessing(
      UUID taskId, String processingOwner, OffsetDateTime leaseExpiresAt);

  Optional<AsyncTask> markCompleted(UUID taskId, Map<String, Object> payload);

  Optional<AsyncTask> markCompletedWithUri(UUID taskId, String uri);

  Optional<AsyncTask> deferCompletion(UUID taskId, Map<String, Object> payload);

  Optional<AsyncTask> markProgress(UUID taskId, Map<String, Object> payload);

  Optional<AsyncTask> markFailed(
      UUID taskId, String errorCode, String errorSummary, Map<String, Object> errorDetail);

  Optional<AsyncTask> markFailed(
      UUID taskId,
      String errorCode,
      String errorSummary,
      Map<String, Object> errorDetail,
      Duration retryDelay);

  boolean extendProcessingLease(UUID taskId, String processingOwner, OffsetDateTime leaseExpiresAt);

  Optional<AsyncTask> markCancelled(UUID taskId);
}
