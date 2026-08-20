package com.forwardmeasure.jpa.identity.repository;

import com.forwardmeasure.jpa.core.entity.AuditedEntity_;
import com.forwardmeasure.jpa.core.repository.AbstractAuditedEntityRepository;
import com.forwardmeasure.jpa.identity.entity.Actor_;
import com.forwardmeasure.jpa.identity.entity.OwnedEntity;
import com.forwardmeasure.jpa.identity.entity.OwnedEntity_;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Standard-JPA repository base for actor-owned entities. */
public abstract class AbstractOwnedEntityRepository<
        T extends OwnedEntity<I>, I extends Serializable>
    extends AbstractAuditedEntityRepository<T, I> {

  public List<T> findByOwnerId(Long ownerId) {
    return query(
        root -> root.get(OwnedEntity_.owner).get(Actor_.id),
        Objects.requireNonNull(ownerId, "ownerId"));
  }

  public List<T> findByOwnerSubjectIdentifier(String subjectIdentifier) {
    return query(
        root -> root.get(OwnedEntity_.owner).get(Actor_.subjectIdentifier),
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier"));
  }

  public Optional<String> findOwnerSubjectIdentifierById(I id) {
    return ownerSubjectIdentifier(this::identifierPath, Objects.requireNonNull(id, "id"));
  }

  public Optional<String> findOwnerSubjectIdentifierByUuid(UUID uuid) {
    return ownerSubjectIdentifier(
        root -> root.get(AuditedEntity_.uuid), Objects.requireNonNull(uuid, "uuid"));
  }

  public long countByOwnerId(Long ownerId) {
    Objects.requireNonNull(ownerId, "ownerId");
    var builder = criteriaBuilder();
    CriteriaQuery<Long> query = builder.createQuery(Long.class);
    Root<T> root = query.from(entityClass());
    query
        .select(builder.count(root))
        .where(builder.equal(root.get(OwnedEntity_.owner).get(Actor_.id), ownerId));
    return entityManager().createQuery(query).getSingleResult();
  }

  public boolean existsByIdAndOwnerId(I id, Long ownerId) {
    return existsByOwnership(
        this::identifierPath,
        Objects.requireNonNull(id, "id"),
        root -> root.get(OwnedEntity_.owner).get(Actor_.id),
        Objects.requireNonNull(ownerId, "ownerId"));
  }

  public boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId) {
    return existsByOwnership(
        root -> root.get(AuditedEntity_.uuid),
        Objects.requireNonNull(uuid, "uuid"),
        root -> root.get(OwnedEntity_.owner).get(Actor_.id),
        Objects.requireNonNull(ownerId, "ownerId"));
  }

  public boolean existsByIdAndOwnerSubjectIdentifier(I id, String subjectIdentifier) {
    return existsByOwnership(
        this::identifierPath,
        Objects.requireNonNull(id, "id"),
        root -> root.get(OwnedEntity_.owner).get(Actor_.subjectIdentifier),
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier"));
  }

  public boolean existsByUuidAndOwnerSubjectIdentifier(UUID uuid, String subjectIdentifier) {
    return existsByOwnership(
        root -> root.get(AuditedEntity_.uuid),
        Objects.requireNonNull(uuid, "uuid"),
        root -> root.get(OwnedEntity_.owner).get(Actor_.subjectIdentifier),
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier"));
  }

  private boolean existsByOwnership(
      Function<Root<T>, Path<?>> resourcePath,
      Object resourceValue,
      Function<Root<T>, Path<?>> ownerPath,
      Object ownerValue) {
    var builder = criteriaBuilder();
    CriteriaQuery<Long> query = builder.createQuery(Long.class);
    Root<T> root = query.from(entityClass());
    query
        .select(builder.count(root))
        .where(
            builder.equal(resourcePath.apply(root), resourceValue),
            builder.equal(ownerPath.apply(root), ownerValue));
    return entityManager().createQuery(query).getSingleResult() > 0L;
  }

  private List<T> query(Function<Root<T>, Path<?>> selectedPath, Object value) {
    var builder = criteriaBuilder();
    CriteriaQuery<T> query = builder.createQuery(entityClass());
    Root<T> root = query.from(entityClass());
    query.select(root).where(builder.equal(selectedPath.apply(root), value));
    return List.copyOf(entityManager().createQuery(query).getResultList());
  }

  private Optional<String> ownerSubjectIdentifier(
      Function<Root<T>, Path<?>> resourcePath, Object value) {
    var builder = criteriaBuilder();
    CriteriaQuery<String> query = builder.createQuery(String.class);
    Root<T> root = query.from(entityClass());
    query.select(root.get(OwnedEntity_.owner).get(Actor_.subjectIdentifier));
    query.where(builder.equal(resourcePath.apply(root), value));
    return entityManager().createQuery(query).setMaxResults(1).getResultList().stream().findFirst();
  }
}
