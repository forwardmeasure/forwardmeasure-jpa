package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.identity.entity.OwnedEntity;
import com.forwardmeasure.jpa.core.entity.AuditedEntity_;
import com.forwardmeasure.jpa.identity.entity.Actor_;
import com.forwardmeasure.jpa.identity.entity.OwnedEntity_;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Panache-native repository base for actor-owned entities.
 */
public interface QuarkusOwnedRepository<
        T extends OwnedEntity<I>, I extends Serializable>
        extends QuarkusAuditedRepository<T, I> {

    default List<T> findByOwnerId(Long ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return List.copyOf(list(ownerIdPath(), ownerId));
    }

    default List<T> findByOwnerSubjectIdentifier(String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return List.copyOf(
                list(ownerSubjectIdentifierPath(), subjectIdentifier));
    }

    default long countByOwnerId(Long ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return count(ownerIdPath(), ownerId);
    }

    default boolean existsByIdAndOwnerId(I id, Long ownerId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        return count("id = ?1 and " + ownerIdPath() + " = ?2", id, ownerId)
                > 0L;
    }

    default boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(ownerId, "ownerId");
        return count(
                AuditedEntity_.UUID + " = ?1 and " + ownerIdPath() + " = ?2",
                uuid,
                ownerId) > 0L;
    }

    default boolean existsByIdAndOwnerSubjectIdentifier(
            I id, String subjectIdentifier) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return count(
                "id = ?1 and " + ownerSubjectIdentifierPath() + " = ?2",
                id,
                subjectIdentifier) > 0L;
    }

    default boolean existsByUuidAndOwnerSubjectIdentifier(
            UUID uuid, String subjectIdentifier) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return count(
                AuditedEntity_.UUID + " = ?1 and "
                        + ownerSubjectIdentifierPath() + " = ?2",
                uuid,
                subjectIdentifier) > 0L;
    }

    private String ownerIdPath() {
        return OwnedEntity_.OWNER + "." + Actor_.ID;
    }

    private String ownerSubjectIdentifierPath() {
        return OwnedEntity_.OWNER + "." + Actor_.SUBJECT_IDENTIFIER;
    }
}
