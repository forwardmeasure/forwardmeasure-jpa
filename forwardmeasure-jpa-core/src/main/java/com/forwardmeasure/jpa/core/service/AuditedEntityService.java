package com.forwardmeasure.jpa.core.service;

import com.forwardmeasure.jpa.core.entity.AuditedEntity;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application-facing persistence operations for audited entities. */
public interface AuditedEntityService<
        T extends AuditedEntity<I>, I extends Serializable>
        extends EntityService<T, I> {

    Optional<T> findByUuid(UUID uuid);

    List<T> findByUuids(Collection<UUID> uuids);

    boolean existsByUuid(UUID uuid);

    boolean deleteByUuid(UUID uuid);
}
