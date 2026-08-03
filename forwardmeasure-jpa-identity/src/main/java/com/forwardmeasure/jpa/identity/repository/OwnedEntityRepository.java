package com.forwardmeasure.jpa.identity.repository;

import com.forwardmeasure.jpa.core.repository.AuditedEntityRepository;
import com.forwardmeasure.jpa.identity.entity.OwnedEntity;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnedEntityRepository<
        T extends OwnedEntity<I>, I extends Serializable>
        extends AuditedEntityRepository<T, I> {

    List<T> findByOwnerId(Long ownerId);

    List<T> findByOwnerSubjectIdentifier(String subjectIdentifier);

    Optional<String> findOwnerSubjectIdentifierById(I id);

    Optional<String> findOwnerSubjectIdentifierByUuid(UUID uuid);

    long countByOwnerId(Long ownerId);

    boolean existsByIdAndOwnerId(I id, Long ownerId);

    boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId);

    boolean existsByIdAndOwnerSubjectIdentifier(
            I id, String subjectIdentifier);

    boolean existsByUuidAndOwnerSubjectIdentifier(
            UUID uuid, String subjectIdentifier);
}
