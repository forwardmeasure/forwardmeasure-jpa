package com.forwardmeasure.jpa.identity.repository;

import com.forwardmeasure.jpa.core.repository.JpaAuditedEntityRepository;
import com.forwardmeasure.jpa.identity.OwnedEntity;
import jakarta.persistence.EntityManager;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
                "entity.owner.id = :ownerId", "ownerId", ownerId);
    }

    @Override
    public List<T> findByOwnerSubjectIdentifier(String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return query(
                "entity.owner.subjectIdentifier = :subjectIdentifier",
                "subjectIdentifier",
                subjectIdentifier);
    }

    @Override
    public Optional<String> findOwnerSubjectIdentifierById(I id) {
        Objects.requireNonNull(id, "id");
        return ownerSubjectIdentifier("entity.id = :id", "id", id);
    }

    @Override
    public Optional<String> findOwnerSubjectIdentifierByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return ownerSubjectIdentifier(
                "entity.uuid = :uuid", "uuid", uuid);
    }

    @Override
    public long countByOwnerId(Long ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return entityManager()
                .createQuery(
                        "select count(entity) from " + entityName()
                                + " entity where entity.owner.id = :ownerId",
                        Long.class)
                .setParameter("ownerId", ownerId)
                .getSingleResult();
    }

    @Override
    public boolean existsByIdAndOwnerId(I id, Long ownerId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        return existsByOwnership(
                "entity.id = :resourceId",
                "resourceId",
                id,
                "entity.owner.id = :owner",
                ownerId);
    }

    @Override
    public boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(ownerId, "ownerId");
        return existsByOwnership(
                "entity.uuid = :resourceId",
                "resourceId",
                uuid,
                "entity.owner.id = :owner",
                ownerId);
    }

    @Override
    public boolean existsByIdAndOwnerSubjectIdentifier(
            I id, String subjectIdentifier) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return existsByOwnership(
                "entity.id = :resourceId",
                "resourceId",
                id,
                "entity.owner.subjectIdentifier = :owner",
                subjectIdentifier);
    }

    @Override
    public boolean existsByUuidAndOwnerSubjectIdentifier(
            UUID uuid, String subjectIdentifier) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return existsByOwnership(
                "entity.uuid = :resourceId",
                "resourceId",
                uuid,
                "entity.owner.subjectIdentifier = :owner",
                subjectIdentifier);
    }

    private boolean existsByOwnership(
            String resourcePredicate,
            String resourceParameter,
            Object resourceValue,
            String ownerPredicate,
            Object ownerValue) {
        return entityManager()
                .createQuery(
                        "select count(entity) from " + entityName()
                                + " entity where " + resourcePredicate
                                + " and " + ownerPredicate,
                        Long.class)
                .setParameter(resourceParameter, resourceValue)
                .setParameter("owner", ownerValue)
                .getSingleResult() > 0L;
    }

    private List<T> query(String predicate, String parameter, Object value) {
        return List.copyOf(entityManager()
                .createQuery(
                        "select entity from " + entityName()
                                + " entity where " + predicate,
                        entityType())
                .setParameter(parameter, value)
                .getResultList());
    }

    private Optional<String> ownerSubjectIdentifier(
            String predicate, String parameter, Object value) {
        return entityManager()
                .createQuery(
                        "select entity.owner.subjectIdentifier from "
                                + entityName()
                                + " entity where " + predicate,
                        String.class)
                .setParameter(parameter, value)
                .getResultStream()
                .findFirst();
    }

    private String entityName() {
        var entity = entityType().getAnnotation(jakarta.persistence.Entity.class);
        return entity != null && !entity.name().isBlank()
                ? entity.name()
                : entityType().getSimpleName();
    }
}
