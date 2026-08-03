package com.forwardmeasure.jpa.core.service;

import com.forwardmeasure.jpa.core.entity.AuditedEntity;
import com.forwardmeasure.jpa.core.repository.AuditedEntityRepository;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Base service implementation for audited entities. */
public abstract class AbstractAuditedEntityService<
        T extends AuditedEntity<I>,
        I extends Serializable,
        R extends AuditedEntityRepository<T, I>>
        extends AbstractEntityService<T, I, R>
        implements AuditedEntityService<T, I> {

    protected AbstractAuditedEntityService(R repository) {
        super(repository);
    }

    @Override
    public Optional<T> findByUuid(UUID uuid) {
        return repository().findByUuid(uuid);
    }

    @Override
    public List<T> findByUuids(Collection<UUID> uuids) {
        return repository().findByUuids(uuids);
    }

    @Override
    public boolean existsByUuid(UUID uuid) {
        return repository().existsByUuid(uuid);
    }

    @Override
    public boolean deleteByUuid(UUID uuid) {
        return repository().deleteByUuid(uuid);
    }
}
