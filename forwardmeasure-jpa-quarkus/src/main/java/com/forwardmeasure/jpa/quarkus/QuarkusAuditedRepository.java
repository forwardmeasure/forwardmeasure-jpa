package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.core.entity.AuditedEntity;
import com.forwardmeasure.jpa.core.entity.AuditedEntity_;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Panache-native repository base for audited entities.
 *
 * <p>This interface is deliberately separate from the provider-neutral
 * repository contract: Panache's {@code findById} returns an entity while the
 * portable contract returns an {@link Optional}, so combining the two APIs
 * would create an invalid Java type hierarchy.
 */
public interface QuarkusAuditedRepository<
        T extends AuditedEntity<I>, I extends Serializable>
        extends PanacheRepositoryBase<T, I> {

    default Optional<T> findByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return find(AuditedEntity_.UUID, uuid).firstResultOptional();
    }

    default List<T> findByUuids(Collection<UUID> uuids) {
        Objects.requireNonNull(uuids, "uuids");
        if (uuids.isEmpty()) {
            return List.of();
        }
        return List.copyOf(list(AuditedEntity_.UUID + " in ?1", uuids));
    }

    default boolean existsByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return count(AuditedEntity_.UUID, uuid) > 0L;
    }

    default long deleteByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return delete(AuditedEntity_.UUID, uuid);
    }
}
