package com.forwardmeasure.jpa.asynctask.entity;

import com.forwardmeasure.jpa.asynctask.converter.AsyncTaskStatusConverter;
import com.forwardmeasure.jpa.asynctask.converter.AsyncTaskTypeConverter;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskStatus;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskType;
import com.forwardmeasure.jpa.core.entity.AuditedEntity;
import com.forwardmeasure.jpa.identity.entity.Actor;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable state and lifecycle metadata for an asynchronous operation. */
@Entity
@Table(name = "async_task")
@SequenceGenerator(
    name = "async_task_id_generator",
    sequenceName = "async_task_id_seq",
    allocationSize = 1)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class AsyncTask extends AuditedEntity<Long> {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(generator = "async_task_id_generator", strategy = GenerationType.SEQUENCE)
  @Column(name = "id")
  private Long id;

  @NotNull
  @Convert(converter = AsyncTaskTypeConverter.class)
  @Column(name = "task_type", nullable = false, length = 50)
  private AsyncTaskType taskType;

  @NotNull
  @Builder.Default
  @Convert(converter = AsyncTaskStatusConverter.class)
  @Column(name = "status", nullable = false, length = 20)
  private AsyncTaskStatus status = AsyncTaskStatus.ACCEPTED;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "max_attempts", nullable = false)
  @Builder.Default
  private int maxAttempts = 3;

  @Column(name = "next_retry_at")
  private OffsetDateTime nextRetryAt;

  @Column(name = "resource_type", length = 50)
  private String resourceType;

  @Column(name = "resource_id")
  private UUID taskResourceId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_id", referencedColumnName = "id")
  private Actor actor;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_payload")
  private Map<String, Object> resultPayload;

  @Column(name = "result_uri")
  private String resultUri;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "progress_payload")
  private Map<String, Object> progressPayload;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "input_payload_json")
  private Map<String, Object> inputPayload;

  @Column(name = "error_code", length = 100)
  private String errorCode;

  @Column(name = "error_message")
  private String errorMessage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "error_detail")
  private Map<String, Object> errorDetail;

  @NotNull
  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "processing_owner", length = 255)
  private String processingOwner;

  @Column(name = "processing_lease_expires_at")
  private OffsetDateTime processingLeaseExpiresAt;

  @Column(name = "idempotency_key", length = 255)
  private String idempotencyKey;

  @Column(name = "idempotency_fingerprint", length = 255)
  private String idempotencyFingerprint;

  @Column(name = "dispatch_topic_path", length = 255)
  private String dispatchTopicPath;

  @Column(name = "dispatch_event_type", length = 255)
  private String dispatchEventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "invocation_metadata_json")
  @Builder.Default
  private Map<String, Object> invocationMetadata = new LinkedHashMap<>();

  public boolean isPending() {
    return status == AsyncTaskStatus.ACCEPTED;
  }

  public boolean isProcessing() {
    return status == AsyncTaskStatus.PROCESSING;
  }

  public boolean isCompleted() {
    return status == AsyncTaskStatus.COMPLETED;
  }

  public boolean isFailed() {
    return status == AsyncTaskStatus.FAILED;
  }

  public boolean isCancelled() {
    return status == AsyncTaskStatus.CANCELLED;
  }

  public boolean isTerminal() {
    return status != null && status.isTerminal();
  }

  public boolean isRetryable() {
    return status == AsyncTaskStatus.ACCEPTED
        && attemptCount > 0
        && attemptCount < maxAttempts
        && nextRetryAt != null;
  }

  public void markProcessing() {
    markProcessing(null, null);
  }

  public void markProcessing(String owner, OffsetDateTime leaseExpiresAt) {
    requireStatus(AsyncTaskStatus.ACCEPTED, "start processing");
    if (attemptCount >= maxAttempts) {
      throw new IllegalStateException("Task has exhausted its maximum attempts");
    }
    status = AsyncTaskStatus.PROCESSING;
    attemptCount++;
    nextRetryAt = null;
    processingOwner = owner;
    processingLeaseExpiresAt = leaseExpiresAt;
    if (startedAt == null) {
      startedAt = now();
    }
    if (resourceType == null && taskType != null) {
      resourceType = taskType.resourceType();
    }
  }

  public void markCompleted(Map<String, Object> payload) {
    requireStatus(AsyncTaskStatus.PROCESSING, "complete");
    status = AsyncTaskStatus.COMPLETED;
    completedAt = now();
    resultPayload = copy(payload);
    resultUri = null;
    clearProcessingLease();
  }

  /** Completes with an implementation-neutral object-storage URI. */
  public void markCompletedWithUri(String uri) {
    requireStatus(AsyncTaskStatus.PROCESSING, "complete");
    if (uri == null || uri.isBlank()) {
      throw new IllegalArgumentException("uri must not be blank");
    }
    status = AsyncTaskStatus.COMPLETED;
    completedAt = now();
    resultUri = uri;
    resultPayload = null;
    clearProcessingLease();
  }

  public void markProgress(Map<String, Object> payload) {
    requireActive("record progress");
    if (payload == null || payload.isEmpty()) {
      progressPayload = null;
      return;
    }
    Map<String, Object> progress =
        progressPayload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(progressPayload);
    progress.putAll(payload);
    progressPayload = progress;
  }

  public void deferCompletion(Map<String, Object> payload) {
    requireStatus(AsyncTaskStatus.PROCESSING, "defer completion");
    status = AsyncTaskStatus.PROCESSING;
    Map<String, Object> progress =
        progressPayload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(progressPayload);
    if (payload != null) {
      progress.putAll(payload);
    }
    progress.put("phase", "AWAITING_DOWNSTREAM_PROCESSING");
    progress.putIfAbsent("records_materialized", 0L);
    progress.putIfAbsent("work_units_completed", 0L);
    progressPayload = progress;
    processingOwner = "downstream_completion";
    processingLeaseExpiresAt = expiresAt;

    long expected = numberValue(progress.get("work_units_expected"), -1L);
    long completed = numberValue(progress.get("work_units_completed"), 0L);
    if (expected >= 0L && completed >= expected) {
      progress.put("phase", "COMPLETED");
      markCompleted(progress);
    }
  }

  public void markFailed(String code, String summary, Map<String, Object> detail) {
    markFailed(code, summary, detail, null);
  }

  public void markFailed(
      String code, String summary, Map<String, Object> detail, Duration retryDelay) {
    requireStatus(AsyncTaskStatus.PROCESSING, "fail");
    if (retryDelay != null && retryDelay.isNegative()) {
      throw new IllegalArgumentException("retryDelay must not be negative");
    }
    errorCode = code;
    errorMessage = summary;
    errorDetail = copy(detail);
    clearProcessingLease();

    if (attemptCount < maxAttempts) {
      Duration delay =
          retryDelay == null
              ? Duration.ofSeconds((long) (30 * Math.pow(4, Math.max(0, attemptCount - 1))))
              : retryDelay;
      nextRetryAt = now().plus(delay);
      status = AsyncTaskStatus.ACCEPTED;
    } else {
      status = AsyncTaskStatus.FAILED;
      completedAt = now();
    }
  }

  public void markCancelled() {
    requireActive("cancel");
    status = AsyncTaskStatus.CANCELLED;
    completedAt = now();
    clearProcessingLease();
  }

  public void markSkipped() {
    requireActive("skip");
    status = AsyncTaskStatus.SKIPPED;
    completedAt = now();
    clearProcessingLease();
  }

  public boolean processingLeaseExpired(OffsetDateTime timestamp) {
    return status == AsyncTaskStatus.PROCESSING
        && (processingLeaseExpiresAt == null || !processingLeaseExpiresAt.isAfter(timestamp));
  }

  public boolean extendProcessingLease(String owner, OffsetDateTime leaseExpiresAt) {
    if (status != AsyncTaskStatus.PROCESSING
        || processingOwner == null
        || !processingOwner.equals(owner)) {
      return false;
    }
    processingLeaseExpiresAt = leaseExpiresAt;
    return true;
  }

  private void clearProcessingLease() {
    processingOwner = null;
    processingLeaseExpiresAt = null;
  }

  private void requireActive(String transition) {
    if (status != AsyncTaskStatus.ACCEPTED && status != AsyncTaskStatus.PROCESSING) {
      throw new IllegalStateException("Cannot " + transition + " task in state " + status);
    }
  }

  private void requireStatus(AsyncTaskStatus required, String transition) {
    if (status != required) {
      throw new IllegalStateException(
          "Cannot " + transition + " task in state " + status + "; required state is " + required);
    }
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  private static Map<String, Object> copy(Map<String, Object> value) {
    return value == null ? null : new LinkedHashMap<>(value);
  }

  private static long numberValue(Object value, long fallback) {
    return value instanceof Number number ? number.longValue() : fallback;
  }
}
