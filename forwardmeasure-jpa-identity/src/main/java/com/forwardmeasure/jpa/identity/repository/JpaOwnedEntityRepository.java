package com.forwardmeasure.jpa.identity.repository;

import com.forwardmeasure.jpa.core.repository.JpaAuditedEntityRepository;
import com.forwardmeasure.jpa.core.entity.AuditedEntity_;
import com.forwardmeasure.jpa.identity.entity.Actor_;
import com.forwardmeasure.jpa.identity.entity.OwnedEntity;
import com.forwardmeasure.jpa.identity.entity.OwnedEntity_;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class JpaOwnedEntityRepository<
        T extends OwnedEntity<I>, I extends Serializable>
        extends JpaAuditedEntityRepository<T, I>
        implements OwnedEntityRepository<T, I> {

    public JpaOwnedEntityRepository(
            Class<T> entityType, EntityManager entityManager) {
        super(entityType, entityManager);
    }

    @Override
    public List<T> findByOwnerId(Long ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return query(
                root -> root.get(OwnedEntity_.owner).get(Actor_.id),
                ownerId);
    }

    @Override
    public List<T> findByOwnerSubjectIdentifier(String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return query(
                root -> root.get(OwnedEntity_.owner)
                        .get(Actor_.subjectIdentifier),
                subjectIdentifier);
    }

    @Override
    public Optional<String> findOwnerSubjectIdentifierById(I id) {
        Objects.requireNonNull(id, "id");
        return ownerSubjectIdentifier(root -> root.get("id"), id);
    }

    @Override
    public Optional<String> findOwnerSubjectIdentifierByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return ownerSubjectIdentifier(
                root -> root.get(AuditedEntity_.uuid),
                uuid);
    }

    @Override
    public long countByOwnerId(Long ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        var builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<T> root = query.from(entityType());
        query.select(builder.count(root)).where(builder.equal(
                root.get(OwnedEntity_.owner).get(Actor_.id),
                ownerId));
        return entityManager().createQuery(query).getSingleResult();
    }

    @Override
    public boolean existsByIdAndOwnerId(I id, Long ownerId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        return existsByOwnership(
                root -> root.get("id"),
                id,
                root -> root.get(OwnedEntity_.owner).get(Actor_.id),
                ownerId);
    }

    @Override
    public boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(ownerId, "ownerId");
        return existsByOwnership(
                root -> root.get(AuditedEntity_.uuid),
                uuid,
                root -> root.get(OwnedEntity_.owner).get(Actor_.id),
                ownerId);
    }

    @Override
    public boolean existsByIdAndOwnerSubjectIdentifier(
            I id, String subjectIdentifier) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return existsByOwnership(
                root -> root.get("id"),
                id,
                root -> root.get(OwnedEntity_.owner)
                        .get(Actor_.subjectIdentifier),
                subjectIdentifier);
    }

    @Override
    public boolean existsByUuidAndOwnerSubjectIdentifier(
            UUID uuid, String subjectIdentifier) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return existsByOwnership(
                root -> root.get(AuditedEntity_.uuid),
                uuid,
                root -> root.get(OwnedEntity_.owner)
                        .get(Actor_.subjectIdentifier),
                subjectIdentifier);
    }

    private boolean existsByOwnership(
            Function<Root<T>, Path<?>> resourcePath,
            Object resourceValue,
            Function<Root<T>, Path<?>> ownerPath,
            Object ownerValue) {
        var builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<T> root = query.from(entityType());
        query.select(builder.count(root)).where(
                builder.equal(resourcePath.apply(root), resourceValue),
                builder.equal(ownerPath.apply(root), ownerValue));
        return entityManager().createQuery(query).getSingleResult() > 0L;
    }

    private List<T> query(
            Function<Root<T>, Path<?>> selectedPath,
            Object value) {
        var builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(entityType());
        Root<T> root = query.from(entityType());
        query.select(root).where(builder.equal(
                selectedPath.apply(root),
                value));
        return List.copyOf(entityManager().createQuery(query)
                .getResultList());
    }

    private Optional<String> ownerSubjectIdentifier(
            Function<Root<T>, Path<?>> resourcePath,
            Object value) {
        var builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<String> query = builder.createQuery(String.class);
        Root<T> root = query.from(entityType());
        query.select(root.get(OwnedEntity_.owner)
                .get(Actor_.subjectIdentifier));
        query.where(builder.equal(resourcePath.apply(root), value));
        return entityManager().createQuery(query)
                .getResultStream()
                .findFirst();
    }
}
