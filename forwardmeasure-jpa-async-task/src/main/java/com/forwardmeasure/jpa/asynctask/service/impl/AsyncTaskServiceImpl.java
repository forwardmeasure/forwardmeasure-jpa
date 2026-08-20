package com.forwardmeasure.jpa.asynctask.service.impl;

import com.forwardmeasure.jpa.asynctask.entity.AsyncTask;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskStatus;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskType;
import com.forwardmeasure.jpa.asynctask.repository.AsyncTaskRepository;
import com.forwardmeasure.jpa.asynctask.service.AsyncTaskService;
import com.forwardmeasure.jpa.core.service.impl.AuditedEntityServiceImpl;
import com.forwardmeasure.jpa.identity.entity.Actor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Standard-JPA asynchronous task service shared by every application host. */
@Transactional
@Singleton
public class AsyncTaskServiceImpl
    extends AuditedEntityServiceImpl<AsyncTask, Long, AsyncTaskRepository>
    implements AsyncTaskService {

  @Inject
  public AsyncTaskServiceImpl(AsyncTaskRepository repository) {
    super(repository);
  }

  @Override
  public Optional<AsyncTask> findByUuidForUpdate(UUID uuid) {
    return repository().findByUuidForUpdate(uuid);
  }

  @Override
  public List<AsyncTask> findByResource(String resourceType, UUID resourceId) {
    return repository().findByResource(resourceType, resourceId);
  }

  @Override
  public List<AsyncTask> findByResourceAndStatus(
      String resourceType, UUID resourceId, AsyncTaskStatus status) {
    return repository().findByResourceAndStatus(resourceType, resourceId, status);
  }

  @Override
  public List<AsyncTask> findRetryable(String taskType, int limit) {
    return repository().findRetryable(taskType, limit);
  }

  @Override
  public List<AsyncTask> findDispatchableRetries(int limit) {
    return repository().findDispatchableRetries(limit);
  }

  @Override
  public List<AsyncTask> findExpiredProcessingLeases(int limit) {
    return repository().findExpiredProcessingLeases(limit);
  }

  @Override
  public Optional<AsyncTask> findByIdempotencyKey(String key) {
    return repository().findByIdempotencyKey(key);
  }

  @Override
  public Optional<AsyncTask> findByIdempotencyFingerprint(String fingerprint) {
    return repository().findByIdempotencyFingerprint(fingerprint);
  }

  @Override
  public List<AsyncTask> listTasks(
      AsyncTaskStatus status, String taskType, String resourceType, int page, int pageSize) {
    return repository().listTasks(status, taskType, resourceType, page, pageSize);
  }

  @Override
  public long countTasks(AsyncTaskStatus status, String taskType, String resourceType) {
    return repository().countTasks(status, taskType, resourceType);
  }

  @Override
  public List<AsyncTask> listByResourceId(
      UUID resourceId, AsyncTaskStatus status, int page, int pageSize) {
    return status == null
        ? repository().listByResourceId(resourceId, page, pageSize)
        : repository().listByResourceIdAndStatus(resourceId, status, page, pageSize);
  }

  @Override
  public long countByResourceId(UUID resourceId, AsyncTaskStatus status) {
    return status == null
        ? repository().countByResourceId(resourceId)
        : repository().countByResourceIdAndStatus(resourceId, status);
  }

  @Override
  public AsyncTask createTask(AsyncTaskType taskType, UUID resourceId, Actor actor) {
    return createTask(taskType, resourceId, actor, null, null);
  }

  @Override
  public AsyncTask createTask(
      AsyncTaskType taskType, UUID resourceId, Actor actor, String idempotencyKey) {
    return createTask(taskType, resourceId, actor, idempotencyKey, null);
  }

  @Override
  public AsyncTask createTask(
      AsyncTaskType taskType,
      UUID resourceId,
      Actor actor,
      String idempotencyKey,
      String idempotencyFingerprint) {
    AsyncTaskType requiredType = Objects.requireNonNull(taskType, "taskType");
    AsyncTask task =
        AsyncTask.builder()
            .taskType(requiredType)
            .resourceType(requiredType.resourceType())
            .taskResourceId(resourceId)
            .status(AsyncTaskStatus.ACCEPTED)
            .maxAttempts(requiredType.defaultMaxAttempts())
            .expiresAt(
                OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(requiredType.defaultExpirySeconds()))
            .actor(actor)
            .idempotencyKey(normalize(idempotencyKey))
            .idempotencyFingerprint(normalize(idempotencyFingerprint))
            .build();
    repository().persistAndFlush(task);
    return task;
  }

  @Override
  public int cancelByResource(String resourceType, UUID resourceId) {
    List<AsyncTask> active = repository().findActiveByResource(resourceType, resourceId);
    active.forEach(AsyncTask::markCancelled);
    repository().flush();
    return active.size();
  }

  @Override
  public long deleteByResource(String resourceType, UUID resourceId) {
    return repository().deleteByResource(resourceType, resourceId);
  }

  @Override
  public long deleteExpired() {
    return repository().deleteExpired();
  }

  @Override
  public Optional<AsyncTask> markProcessing(UUID taskId) {
    return update(taskId, AsyncTask::markProcessing);
  }

  @Override
  public Optional<AsyncTask> markProcessing(
      UUID taskId, String processingOwner, OffsetDateTime leaseExpiresAt) {
    return update(taskId, task -> task.markProcessing(processingOwner, leaseExpiresAt));
  }

  @Override
  public Optional<AsyncTask> markCompleted(UUID taskId, Map<String, Object> payload) {
    return update(taskId, task -> task.markCompleted(payload));
  }

  @Override
  public Optional<AsyncTask> markCompletedWithUri(UUID taskId, String uri) {
    return update(taskId, task -> task.markCompletedWithUri(uri));
  }

  @Override
  public Optional<AsyncTask> deferCompletion(UUID taskId, Map<String, Object> payload) {
    return update(taskId, task -> task.deferCompletion(payload));
  }

  @Override
  public Optional<AsyncTask> markProgress(UUID taskId, Map<String, Object> payload) {
    return update(taskId, task -> task.markProgress(payload));
  }

  @Override
  public Optional<AsyncTask> markFailed(
      UUID taskId, String errorCode, String errorSummary, Map<String, Object> errorDetail) {
    return markFailed(taskId, errorCode, errorSummary, errorDetail, null);
  }

  @Override
  public Optional<AsyncTask> markFailed(
      UUID taskId,
      String errorCode,
      String errorSummary,
      Map<String, Object> errorDetail,
      Duration retryDelay) {
    return update(
        taskId, task -> task.markFailed(errorCode, errorSummary, errorDetail, retryDelay));
  }

  @Override
  public boolean extendProcessingLease(
      UUID taskId, String processingOwner, OffsetDateTime leaseExpiresAt) {
    return repository()
        .findByUuidForUpdate(taskId)
        .map(
            task -> {
              boolean extended = task.extendProcessingLease(processingOwner, leaseExpiresAt);
              if (extended) {
                repository().flush();
              }
              return extended;
            })
        .orElse(false);
  }

  @Override
  public Optional<AsyncTask> markCancelled(UUID taskId) {
    return update(taskId, AsyncTask::markCancelled);
  }

  private Optional<AsyncTask> update(UUID taskId, Consumer<AsyncTask> transition) {
    return repository()
        .findByUuidForUpdate(taskId)
        .map(
            task -> {
              transition.accept(task);
              repository().flush();
              return task;
            });
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
