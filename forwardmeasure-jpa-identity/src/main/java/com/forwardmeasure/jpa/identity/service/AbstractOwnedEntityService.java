package com.forwardmeasure.jpa.identity.service;

import com.forwardmeasure.jpa.core.service.AbstractAuditedEntityService;
import com.forwardmeasure.jpa.identity.entity.OwnedEntity;
import com.forwardmeasure.jpa.identity.repository.OwnedEntityRepository;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Base service implementation for actor-owned entities. */
public abstract class AbstractOwnedEntityService<
        T extends OwnedEntity<I>,
        I extends Serializable,
        R extends OwnedEntityRepository<T, I>>
        extends AbstractAuditedEntityService<T, I, R>
        implements OwnedEntityService<T, I> {

    protected AbstractOwnedEntityService(R repository) {
        super(repository);
    }

    @Override
    public List<T> findByOwnerId(Long ownerId) {
        return repository().findByOwnerId(ownerId);
    }

    @Override
    public List<T> findByOwnerSubjectIdentifier(String subjectIdentifier) {
        return repository().findByOwnerSubjectIdentifier(subjectIdentifier);
    }

    @Override
    public Optional<String> findOwnerSubjectIdentifierById(I id) {
        return repository().findOwnerSubjectIdentifierById(id);
    }

    @Override
    public Optional<String> findOwnerSubjectIdentifierByUuid(UUID uuid) {
        return repository().findOwnerSubjectIdentifierByUuid(uuid);
    }

    @Override
    public long countByOwnerId(Long ownerId) {
        return repository().countByOwnerId(ownerId);
    }

    @Override
    public boolean existsByIdAndOwnerId(I id, Long ownerId) {
        return repository().existsByIdAndOwnerId(id, ownerId);
    }

    @Override
    public boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId) {
        return repository().existsByUuidAndOwnerId(uuid, ownerId);
    }

    @Override
    public boolean existsByIdAndOwnerSubjectIdentifier(
            I id,
            String subjectIdentifier) {
        return repository().existsByIdAndOwnerSubjectIdentifier(
                id,
                subjectIdentifier);
    }

    @Override
    public boolean existsByUuidAndOwnerSubjectIdentifier(
            UUID uuid,
            String subjectIdentifier) {
        return repository().existsByUuidAndOwnerSubjectIdentifier(
                uuid,
                subjectIdentifier);
    }
}
