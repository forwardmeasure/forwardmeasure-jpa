package com.forwardmeasure.jpa.micronaut;

import com.forwardmeasure.jpa.core.AuditedEntity;
import io.micronaut.data.repository.PageableRepository;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base interface for concrete Micronaut Data repositories. Consumers annotate
 * their concrete subinterface with {@code @Repository}.
 */
public interface MicronautAuditedRepository<
        T extends AuditedEntity<I>, I extends Serializable>
        extends PageableRepository<T, I> {

    Optional<T> findByUuid(UUID uuid);

    List<T> findByUuidIn(Collection<UUID> uuids);

    boolean existsByUuid(UUID uuid);

    long deleteByUuid(UUID uuid);
}
