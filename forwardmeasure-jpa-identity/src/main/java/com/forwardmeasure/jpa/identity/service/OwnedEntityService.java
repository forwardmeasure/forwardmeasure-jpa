package com.forwardmeasure.jpa.identity.service;

import com.forwardmeasure.jpa.core.service.AuditedEntityService;
import com.forwardmeasure.jpa.identity.entity.OwnedEntity;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application-facing persistence operations for actor-owned entities. */
public interface OwnedEntityService<T extends OwnedEntity<I>, I extends Serializable>
    extends AuditedEntityService<T, I> {

  List<T> findByOwnerId(Long ownerId);

  List<T> findByOwnerSubjectIdentifier(String subjectIdentifier);

  Optional<String> findOwnerSubjectIdentifierById(I id);

  Optional<String> findOwnerSubjectIdentifierByUuid(UUID uuid);

  long countByOwnerId(Long ownerId);

  boolean existsByIdAndOwnerId(I id, Long ownerId);

  boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId);

  boolean existsByIdAndOwnerSubjectIdentifier(I id, String subjectIdentifier);

  boolean existsByUuidAndOwnerSubjectIdentifier(UUID uuid, String subjectIdentifier);
}
