package com.forwardmeasure.jpa.core.repository;

import com.forwardmeasure.jpa.core.AbstractBaseEntity;
import com.forwardmeasure.jpa.core.query.JpaSpecification;
import com.forwardmeasure.jpa.core.query.Page;
import com.forwardmeasure.jpa.core.query.PageRequest;
import com.forwardmeasure.jpa.core.query.SortDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Provider-neutral implementation of the base repository contract.
 *
 * <p>The entity manager remains owned by the host framework. This class does
 * not open transactions or retain an entity manager beyond a method call.
 */
public class JpaEntityRepository<
        T extends AbstractBaseEntity<I>, I extends Serializable>
        implements EntityRepository<T, I> {

    private final Class<T> entityType;
    private final EntityManager entityManager;

    public JpaEntityRepository(Class<T> entityType, EntityManager entityManager) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.entityManager =
                Objects.requireNonNull(entityManager, "entityManager");
    }

    protected final Class<T> entityType() {
        return entityType;
    }

    protected final EntityManager entityManager() {
        return entityManager;
    }

    @Override
    public T save(T entity) {
        Objects.requireNonNull(entity, "entity");
        if (entity.getId() == null) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    @Override
    public Optional<T> findById(I id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(entityManager.find(entityType, id));
    }

    @Override
    public List<T> findAll() {
        CriteriaQuery<T> query = entityManager.getCriteriaBuilder()
                .createQuery(entityType);
        query.select(query.from(entityType));
        return List.copyOf(entityManager.createQuery(query).getResultList());
    }

    @Override
    public Page<T> findAll(PageRequest pageRequest) {
        return findAll(pageRequest, null);
    }

    @Override
    public Page<T> findAll(
            PageRequest pageRequest, JpaSpecification<T> specification) {
        Objects.requireNonNull(pageRequest, "pageRequest");
        var builder = entityManager.getCriteriaBuilder();

        CriteriaQuery<T> dataQuery = builder.createQuery(entityType);
        Root<T> dataRoot = dataQuery.from(entityType);
        dataQuery.select(dataRoot);
        if (specification != null) {
            dataQuery.where(specification.toPredicate(
                    dataRoot, dataQuery, builder));
        }
        if (pageRequest.sort().isEmpty()) {
            dataQuery.orderBy(builder.asc(dataRoot.get("id")));
        } else {
            dataQuery.orderBy(pageRequest.sort().stream()
                    .map(sort -> sort.direction() == SortDirection.ASCENDING
                            ? builder.asc(resolvePath(
                                    dataRoot, sort.property()))
                            : builder.desc(resolvePath(
                                    dataRoot, sort.property())))
                    .toList());
        }

        List<T> items = List.copyOf(entityManager.createQuery(dataQuery)
                .setFirstResult(pageRequest.offset())
                .setMaxResults(pageRequest.limit())
                .getResultList());

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<T> countRoot = countQuery.from(entityType);
        if (specification != null) {
            countQuery.where(specification.toPredicate(
                    countRoot, countQuery, builder));
        }
        countQuery.select(countQuery.isDistinct()
                ? builder.countDistinct(countRoot)
                : builder.count(countRoot));
        long totalItems =
                entityManager.createQuery(countQuery).getSingleResult();

        return new Page<>(
                items,
                totalItems,
                pageRequest.offset(),
                pageRequest.limit());
    }

    @Override
    public long count() {
        var builder = entityManager.getCriteriaBuilder();
        var query = builder.createQuery(Long.class);
        var root = query.from(entityType);
        query.select(builder.count(root));
        return entityManager.createQuery(query).getSingleResult();
    }

    @Override
    public boolean deleteById(I id) {
        Optional<T> entity = findById(id);
        entity.ifPresent(entityManager::remove);
        return entity.isPresent();
    }

    @Override
    public void flush() {
        entityManager.flush();
    }

    @Override
    public void detach(T entity) {
        entityManager.detach(Objects.requireNonNull(entity, "entity"));
    }

    private Path<?> resolvePath(Root<T> root, String property) {
        Path<?> path = root;
        for (String segment : property.split("\\.", -1)) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException(
                        "Sort property contains an empty path segment");
            }
            path = path.get(segment);
        }
        return path;
    }
}
