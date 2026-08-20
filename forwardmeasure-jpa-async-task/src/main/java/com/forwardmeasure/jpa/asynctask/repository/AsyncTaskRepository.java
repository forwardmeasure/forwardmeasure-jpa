package com.forwardmeasure.jpa.asynctask.repository;

import com.forwardmeasure.jpa.asynctask.entity.AsyncTask;
import com.forwardmeasure.jpa.asynctask.entity.AsyncTask_;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskStatus;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskType;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskTypeDefinition;
import com.forwardmeasure.jpa.core.entity.AuditedEntity_;
import com.forwardmeasure.jpa.core.repository.AbstractAuditedEntityRepository;
import jakarta.inject.Singleton;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Standard-JPA repository for asynchronous task lifecycle state. */
@Singleton
public class AsyncTaskRepository extends AbstractAuditedEntityRepository<AsyncTask, Long> {

  public Optional<AsyncTask> findByUuidForUpdate(UUID uuid) {
    return findByUuid(uuid, LockModeType.PESSIMISTIC_WRITE);
  }

  public List<AsyncTask> findByResource(String resourceType, UUID resourceId) {
    return query(
        List.of(
            equal(AsyncTask_.resourceType, resourceType),
            equal(AsyncTask_.taskResourceId, resourceId)),
        List.of());
  }

  public List<AsyncTask> findByResourceAndStatus(
      String resourceType, UUID resourceId, AsyncTaskStatus status) {
    return query(
        List.of(
            equal(AsyncTask_.resourceType, resourceType),
            equal(AsyncTask_.taskResourceId, resourceId),
            equal(AsyncTask_.status, status)),
        List.of());
  }

  public List<AsyncTask> findRetryable(String taskType, int limit) {
    return query(
        List.of(
            equal(AsyncTask_.taskType, persistedType(taskType)),
            equal(AsyncTask_.status, AsyncTaskStatus.ACCEPTED),
            lessThanOrEqualTo(AsyncTask_.nextRetryAt, now())),
        List.of(),
        0,
        requirePositive(limit, "limit"));
  }

  public List<AsyncTask> findDispatchableRetries(int limit) {
    return query(
        List.of(
            equal(AsyncTask_.status, AsyncTaskStatus.ACCEPTED),
            lessThanOrEqualTo(AsyncTask_.nextRetryAt, now()),
            isNotNull(AsyncTask_.dispatchTopicPath),
            isNotNull(AsyncTask_.dispatchEventType)),
        List.of(ascending(AsyncTask_.nextRetryAt), ascending(AuditedEntity_.createdAt)),
        0,
        requirePositive(limit, "limit"));
  }

  public List<AsyncTask> findExpiredProcessingLeases(int limit) {
    var builder = criteriaBuilder();
    CriteriaQuery<AsyncTask> query = criteriaQuery();
    Root<AsyncTask> task = root(query);
    query
        .select(task)
        .where(
            builder.equal(task.get(AsyncTask_.status), AsyncTaskStatus.PROCESSING),
            builder.or(
                builder.isNull(task.get(AsyncTask_.processingLeaseExpiresAt)),
                builder.lessThanOrEqualTo(task.get(AsyncTask_.processingLeaseExpiresAt), now())));
    query.orderBy(builder.asc(task.get(AuditedEntity_.updatedAt)));
    return limited(query, 0, requirePositive(limit, "limit"));
  }

  public Optional<AsyncTask> findByIdempotencyKey(String key) {
    return optionalByText(AsyncTask_.idempotencyKey, key);
  }

  public Optional<AsyncTask> findByIdempotencyFingerprint(String fingerprint) {
    return optionalByText(AsyncTask_.idempotencyFingerprint, fingerprint);
  }

  public List<AsyncTask> listTasks(
      AsyncTaskStatus status, String taskType, String resourceType, int page, int pageSize) {
    List<PredicateFactory> predicates = new ArrayList<>();
    if (status != null) {
      predicates.add(equal(AsyncTask_.status, status));
    }
    if (hasText(taskType)) {
      predicates.add(equal(AsyncTask_.taskType, persistedType(taskType)));
    }
    if (hasText(resourceType)) {
      predicates.add(equal(AsyncTask_.resourceType, resourceType));
    }
    return query(
        predicates,
        List.of(descending(AuditedEntity_.createdAt)),
        offset(page, pageSize),
        requirePositive(pageSize, "pageSize"));
  }

  public long countTasks(AsyncTaskStatus status, String taskType, String resourceType) {
    List<PredicateFactory> predicates = new ArrayList<>();
    if (status != null) {
      predicates.add(equal(AsyncTask_.status, status));
    }
    if (hasText(taskType)) {
      predicates.add(equal(AsyncTask_.taskType, persistedType(taskType)));
    }
    if (hasText(resourceType)) {
      predicates.add(equal(AsyncTask_.resourceType, resourceType));
    }
    return count(predicates);
  }

  public List<AsyncTask> listByResourceId(UUID resourceId, int page, int pageSize) {
    return listByResourceId(resourceId, null, page, pageSize);
  }

  public List<AsyncTask> listByResourceIdAndStatus(
      UUID resourceId, AsyncTaskStatus status, int page, int pageSize) {
    return listByResourceId(resourceId, status, page, pageSize);
  }

  public long countByResourceId(UUID resourceId) {
    return count(
        List.of(
            equal(AsyncTask_.taskResourceId, Objects.requireNonNull(resourceId, "resourceId"))));
  }

  public long countByResourceIdAndStatus(UUID resourceId, AsyncTaskStatus status) {
    return count(
        List.of(
            equal(AsyncTask_.taskResourceId, Objects.requireNonNull(resourceId, "resourceId")),
            equal(AsyncTask_.status, Objects.requireNonNull(status, "status"))));
  }

  public List<AsyncTask> findActiveByResource(String resourceType, UUID resourceId) {
    var builder = criteriaBuilder();
    CriteriaQuery<AsyncTask> query = criteriaQuery();
    Root<AsyncTask> task = root(query);
    query
        .select(task)
        .where(
            builder.equal(
                task.get(AsyncTask_.resourceType),
                Objects.requireNonNull(resourceType, "resourceType")),
            builder.equal(
                task.get(AsyncTask_.taskResourceId),
                Objects.requireNonNull(resourceId, "resourceId")),
            task.get(AsyncTask_.status).in(AsyncTaskStatus.ACCEPTED, AsyncTaskStatus.PROCESSING));
    return List.copyOf(entityManager().createQuery(query).getResultList());
  }

  public long deleteByResource(String resourceType, UUID resourceId) {
    var builder = criteriaBuilder();
    CriteriaDelete<AsyncTask> delete = builder.createCriteriaDelete(AsyncTask.class);
    Root<AsyncTask> task = delete.from(AsyncTask.class);
    delete.where(
        builder.equal(
            task.get(AsyncTask_.resourceType),
            Objects.requireNonNull(resourceType, "resourceType")),
        builder.equal(
            task.get(AsyncTask_.taskResourceId), Objects.requireNonNull(resourceId, "resourceId")));
    return entityManager().createQuery(delete).executeUpdate();
  }

  public long deleteExpired() {
    var builder = criteriaBuilder();
    CriteriaDelete<AsyncTask> delete = builder.createCriteriaDelete(AsyncTask.class);
    Root<AsyncTask> task = delete.from(AsyncTask.class);
    delete.where(
        builder.lessThanOrEqualTo(task.get(AsyncTask_.expiresAt), now()),
        task.get(AsyncTask_.status)
            .in(
                AsyncTaskStatus.COMPLETED,
                AsyncTaskStatus.FAILED,
                AsyncTaskStatus.CANCELLED,
                AsyncTaskStatus.SKIPPED));
    return entityManager().createQuery(delete).executeUpdate();
  }

  private List<AsyncTask> listByResourceId(
      UUID resourceId, AsyncTaskStatus status, int page, int pageSize) {
    List<PredicateFactory> predicates = new ArrayList<>();
    predicates.add(
        equal(AsyncTask_.taskResourceId, Objects.requireNonNull(resourceId, "resourceId")));
    if (status != null) {
      predicates.add(equal(AsyncTask_.status, status));
    }
    return query(
        predicates,
        List.of(descending(AuditedEntity_.createdAt)),
        offset(page, pageSize),
        requirePositive(pageSize, "pageSize"));
  }

  private Optional<AsyncTask> optionalByText(
      jakarta.persistence.metamodel.SingularAttribute<AsyncTask, String> attribute, String value) {
    if (!hasText(value)) {
      return Optional.empty();
    }
    return query(List.of(equal(attribute, value)), List.of()).stream().findFirst();
  }

  private List<AsyncTask> query(List<PredicateFactory> predicates, List<OrderFactory> orders) {
    return query(predicates, orders, 0, Integer.MAX_VALUE);
  }

  private List<AsyncTask> query(
      List<PredicateFactory> predicates, List<OrderFactory> orders, int offset, int limit) {
    CriteriaQuery<AsyncTask> query = criteriaQuery();
    Root<AsyncTask> task = root(query);
    query.select(task);
    if (!predicates.isEmpty()) {
      query.where(
          predicates.stream()
              .map(predicate -> predicate.create(criteriaBuilder(), task))
              .toArray(Predicate[]::new));
    }
    if (!orders.isEmpty()) {
      query.orderBy(orders.stream().map(order -> order.create(criteriaBuilder(), task)).toList());
    }
    return limited(query, offset, limit);
  }

  private List<AsyncTask> limited(CriteriaQuery<AsyncTask> query, int offset, int limit) {
    return List.copyOf(
        entityManager()
            .createQuery(query)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList());
  }

  private long count(List<PredicateFactory> predicates) {
    CriteriaBuilder builder = criteriaBuilder();
    CriteriaQuery<Long> query = builder.createQuery(Long.class);
    Root<AsyncTask> task = query.from(AsyncTask.class);
    query.select(builder.count(task));
    if (!predicates.isEmpty()) {
      query.where(
          predicates.stream()
              .map(predicate -> predicate.create(builder, task))
              .toArray(Predicate[]::new));
    }
    return entityManager().createQuery(query).getSingleResult();
  }

  private static <V> PredicateFactory equal(
      jakarta.persistence.metamodel.SingularAttribute<AsyncTask, V> attribute, V value) {
    return (builder, task) -> builder.equal(task.get(attribute), Objects.requireNonNull(value));
  }

  private static <V extends Comparable<? super V>> PredicateFactory lessThanOrEqualTo(
      jakarta.persistence.metamodel.SingularAttribute<AsyncTask, V> attribute, V value) {
    return (builder, task) ->
        builder.lessThanOrEqualTo(task.get(attribute), Objects.requireNonNull(value));
  }

  private static PredicateFactory isNotNull(
      jakarta.persistence.metamodel.SingularAttribute<AsyncTask, ?> attribute) {
    return (builder, task) -> builder.isNotNull(task.get(attribute));
  }

  private static OrderFactory ascending(
      jakarta.persistence.metamodel.SingularAttribute<? super AsyncTask, ?> attribute) {
    return (builder, task) -> builder.asc(task.get(attribute));
  }

  private static OrderFactory descending(
      jakarta.persistence.metamodel.SingularAttribute<? super AsyncTask, ?> attribute) {
    return (builder, task) -> builder.desc(task.get(attribute));
  }

  private static int offset(int page, int pageSize) {
    if (page < 0) {
      throw new IllegalArgumentException("page must not be negative");
    }
    int size = requirePositive(pageSize, "pageSize");
    return Math.multiplyExact(page, size);
  }

  private static int requirePositive(int value, String field) {
    if (value <= 0) {
      throw new IllegalArgumentException(field + " must be greater than zero");
    }
    return value;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  private static AsyncTaskType persistedType(String value) {
    if (!hasText(value)) {
      throw new IllegalArgumentException("taskType must not be blank");
    }
    return new PersistedAsyncTaskType(value);
  }

  @FunctionalInterface
  private interface PredicateFactory {
    Predicate create(CriteriaBuilder builder, Root<AsyncTask> task);
  }

  @FunctionalInterface
  private interface OrderFactory {
    Order create(CriteriaBuilder builder, Root<AsyncTask> task);
  }

  private record PersistedAsyncTaskType(String value) implements AsyncTaskType {

    @Override
    public AsyncTaskTypeDefinition definition() {
      return AsyncTaskTypeDefinition.of(value, "query");
    }

    @Override
    public String name() {
      return value;
    }
  }
}
