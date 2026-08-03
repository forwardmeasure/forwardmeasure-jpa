package com.forwardmeasure.jpa.micronaut;

import com.forwardmeasure.jpa.identity.entity.OwnedEntity;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public interface MicronautOwnedRepository<
        T extends OwnedEntity<I>, I extends Serializable>
        extends MicronautAuditedRepository<T, I> {

    List<T> findByOwnerId(Long ownerId);

    List<T> findByOwnerSubjectIdentifier(String subjectIdentifier);

    long countByOwnerId(Long ownerId);

    boolean existsByIdAndOwnerId(I id, Long ownerId);

    boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId);

    boolean existsByIdAndOwnerSubjectIdentifier(
            I id, String subjectIdentifier);

    boolean existsByUuidAndOwnerSubjectIdentifier(
            UUID uuid, String subjectIdentifier);
}
