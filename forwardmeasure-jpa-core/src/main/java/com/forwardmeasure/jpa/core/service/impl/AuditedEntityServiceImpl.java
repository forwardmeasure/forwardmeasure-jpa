package com.forwardmeasure.jpa.core.service.impl;

import com.forwardmeasure.jpa.core.entity.AuditedEntity;
import com.forwardmeasure.jpa.core.repository.AbstractAuditedEntityRepository;
import com.forwardmeasure.jpa.core.service.AuditedEntityService;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AuditedEntityServiceImpl<
        T extends AuditedEntity<I>,
        I extends Serializable,
        R extends AbstractAuditedEntityRepository<T, I>>
        extends AbstractBaseServiceImpl<T, I, R>
        implements AuditedEntityService<T, I> {

    protected AuditedEntityServiceImpl(R repository) {
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
