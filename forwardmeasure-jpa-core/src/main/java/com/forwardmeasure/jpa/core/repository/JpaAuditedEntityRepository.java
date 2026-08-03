package com.forwardmeasure.jpa.core.repository;

import com.forwardmeasure.jpa.core.entity.AuditedEntity;
import com.forwardmeasure.jpa.core.entity.AuditedEntity_;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
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
        var builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(entityType());
        Root<T> root = query.from(entityType());
        query.select(root).where(builder.equal(
                root.get(AuditedEntity_.uuid),
                uuid));
        return entityManager().createQuery(query)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<T> findByUuids(Collection<UUID> uuids) {
        Objects.requireNonNull(uuids, "uuids");
        if (uuids.isEmpty()) {
            return List.of();
        }
        var builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(entityType());
        Root<T> root = query.from(entityType());
        query.select(root).where(root.get(AuditedEntity_.uuid)
                .in(List.copyOf(uuids)));
        return List.copyOf(entityManager().createQuery(query)
                .getResultList());
    }

    @Override
    public boolean existsByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        var builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<T> root = query.from(entityType());
        query.select(builder.count(root)).where(builder.equal(
                root.get(AuditedEntity_.uuid),
                uuid));
        return entityManager().createQuery(query).getSingleResult() > 0L;
    }

    @Override
    public boolean deleteByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        var builder = entityManager().getCriteriaBuilder();
        CriteriaDelete<T> delete = builder.createCriteriaDelete(entityType());
        Root<T> root = delete.from(entityType());
        delete.where(builder.equal(root.get(AuditedEntity_.uuid), uuid));
        return entityManager().createQuery(delete).executeUpdate() > 0;
    }
}
