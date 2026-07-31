package com.forwardmeasure.jpa.core.repository;

import com.forwardmeasure.jpa.core.AuditedEntity;
import jakarta.persistence.EntityManager;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Standard-JPA implementation of audited-identity lookups.
 */
public class JpaAuditedEntityRepository<
        T extends AuditedEntity<I>, I extends Serializable>
        extends JpaEntityRepository<T, I>
        implements AuditedEntityRepository<T, I> {

    public JpaAuditedEntityRepository(
            Class<T> entityType, EntityManager entityManager) {
        super(entityType, entityManager);
    }

    @Override
    public Optional<T> findByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return entityManager()
                .createQuery(
                        "select entity from " + entityName()
                                + " entity where entity.uuid = :uuid",
                        entityType())
                .setParameter("uuid", uuid)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<T> findByUuids(Collection<UUID> uuids) {
        Objects.requireNonNull(uuids, "uuids");
        if (uuids.isEmpty()) {
            return List.of();
        }
        return List.copyOf(entityManager()
                .createQuery(
                        "select entity from " + entityName()
                                + " entity where entity.uuid in :uuids",
                        entityType())
                .setParameter("uuids", List.copyOf(uuids))
                .getResultList());
    }

    @Override
    public boolean existsByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return entityManager()
                .createQuery(
                        "select count(entity) from " + entityName()
                                + " entity where entity.uuid = :uuid",
                        Long.class)
                .setParameter("uuid", uuid)
                .getSingleResult() > 0L;
    }

    @Override
    public boolean deleteByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return entityManager()
                .createQuery(
                        "delete from " + entityName()
                                + " entity where entity.uuid = :uuid")
                .setParameter("uuid", uuid)
                .executeUpdate() > 0;
    }

    private String entityName() {
        var entity = entityType().getAnnotation(jakarta.persistence.Entity.class);
        return entity != null && !entity.name().isBlank()
                ? entity.name()
                : entityType().getSimpleName();
    }
}
