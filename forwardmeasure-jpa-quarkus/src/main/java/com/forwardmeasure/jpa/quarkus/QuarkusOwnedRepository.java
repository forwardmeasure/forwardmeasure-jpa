package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.identity.OwnedEntity;
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
        return List.copyOf(list("owner.id", ownerId));
    }

    default List<T> findByOwnerSubjectIdentifier(String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return List.copyOf(
                list("owner.subjectIdentifier", subjectIdentifier));
    }

    default long countByOwnerId(Long ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return count("owner.id", ownerId);
    }

    default boolean existsByIdAndOwnerId(I id, Long ownerId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        return count("id = ?1 and owner.id = ?2", id, ownerId) > 0L;
    }

    default boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(ownerId, "ownerId");
        return count("uuid = ?1 and owner.id = ?2", uuid, ownerId) > 0L;
    }

    default boolean existsByIdAndOwnerSubjectIdentifier(
            I id, String subjectIdentifier) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return count(
                "id = ?1 and owner.subjectIdentifier = ?2",
                id,
                subjectIdentifier) > 0L;
    }

    default boolean existsByUuidAndOwnerSubjectIdentifier(
            UUID uuid, String subjectIdentifier) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return count(
                "uuid = ?1 and owner.subjectIdentifier = ?2",
                uuid,
                subjectIdentifier) > 0L;
    }
}
