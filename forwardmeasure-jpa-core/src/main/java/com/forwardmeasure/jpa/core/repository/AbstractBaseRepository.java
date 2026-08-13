package com.forwardmeasure.jpa.core.repository;

import com.forwardmeasure.jpa.core.entity.AbstractBaseEntity;
import com.forwardmeasure.jpa.core.query.JpaSpecification;
import com.forwardmeasure.jpa.core.query.Page;
import com.forwardmeasure.jpa.core.query.PageRequest;
import com.forwardmeasure.jpa.core.query.SortDirection;
import com.forwardmeasure.jpa.core.query.SortOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Standard-JPA repository base shared by every supported application host. */
public abstract class AbstractBaseRepository<
        T extends AbstractBaseEntity<I>, I extends Serializable> {

    @PersistenceContext
    EntityManager entityManager;

    private volatile Class<T> entityClass;

    protected final EntityManager entityManager() {
        if (entityManager == null) {
            throw new IllegalStateException(
                    "No persistence context was injected into "
                            + getClass().getName());
        }
        return entityManager;
    }

    /**
     * Connects a repository created by a framework factory to that
     * framework's transaction-scoped entity-manager proxy.
     *
     * <p>Application code never calls this method. Quarkus normally injects
     * the {@link PersistenceContext} field directly; Spring and Micronaut
     * adapter configuration uses this hook when it creates the same common
     * repository class through a framework factory.
     */
    public final void bindPersistenceContext(EntityManager context) {
        EntityManager required = Objects.requireNonNull(context, "context");
        if (entityManager != null && entityManager != required) {
            throw new IllegalStateException(
                    "Persistence context is already bound for "
                            + getClass().getName());
        }
        entityManager = required;
    }

    @SuppressWarnings("unchecked")
    public final Class<T> entityClass() {
        Class<T> resolved = entityClass;
        if (resolved == null) {
            resolved = (Class<T>) RepositoryTypeResolver.resolveEntityClass(
                    getClass());
            entityClass = resolved;
        }
        return resolved;
    }

    public void persist(T entity) {
        entityManager().persist(Objects.requireNonNull(entity, "entity"));
    }

    public void persistAndFlush(T entity) {
        persist(entity);
        flush();
    }

    public T merge(T entity) {
        return entityManager().merge(Objects.requireNonNull(entity, "entity"));
    }

    public void persist(Iterable<T> entities) {
        Objects.requireNonNull(entities, "entities").forEach(this::persist);
    }

    @SafeVarargs
    public final void persist(T first, T... remaining) {
        persist(first);
        Objects.requireNonNull(remaining, "remaining");
        for (T entity : remaining) {
            persist(entity);
        }
    }

    public void delete(T entity) {
        Objects.requireNonNull(entity, "entity");
        entityManager().remove(isPersistent(entity) ? entity : merge(entity));
    }

    public boolean deleteById(I id) {
        Optional<T> existing = findByIdOptional(id);
        existing.ifPresent(this::delete);
        return existing.isPresent();
    }

    public long deleteAll() {
        var builder = entityManager().getCriteriaBuilder();
        CriteriaDelete<T> delete = builder.createCriteriaDelete(entityClass());
        delete.from(entityClass());
        return entityManager().createQuery(delete).executeUpdate();
    }

    public boolean isPersistent(T entity) {
        return entityManager().contains(Objects.requireNonNull(entity, "entity"));
    }

    public T findById(I id) {
        return entityManager().find(entityClass(), requireId(id));
    }

    public T findById(I id, LockModeType lockMode) {
        return entityManager().find(
                entityClass(),
                requireId(id),
                Objects.requireNonNull(lockMode, "lockMode"));
    }

    public Optional<T> findByIdOptional(I id) {
        return Optional.ofNullable(findById(id));
    }

    public Optional<T> findByIdOptional(I id, LockModeType lockMode) {
        return Optional.ofNullable(findById(id, lockMode));
    }

    public List<T> listAll() {
        CriteriaQuery<T> query = criteriaQuery();
        query.select(root(query));
        return List.copyOf(entityManager().createQuery(query).getResultList());
    }

    public Stream<T> streamAll() {
        CriteriaQuery<T> query = criteriaQuery();
        query.select(root(query));
        return entityManager().createQuery(query).getResultStream();
    }

    public Page<T> page(PageRequest request) {
        return page(request, null);
    }

    public Page<T> page(
            PageRequest request, JpaSpecification<T> specification) {
        Objects.requireNonNull(request, "request");
        var builder = entityManager().getCriteriaBuilder();

        CriteriaQuery<T> dataQuery = builder.createQuery(entityClass());
        Root<T> dataRoot = dataQuery.from(entityClass());
        dataQuery.select(dataRoot);
        if (specification != null) {
            dataQuery.where(specification.toPredicate(
                    dataRoot, dataQuery, builder));
        }

        List<SortOrder> sort = request.sort().isEmpty()
                ? List.of(new SortOrder(
                        identifierAttribute().getName(),
                        SortDirection.ASCENDING))
                : request.sort();
        dataQuery.orderBy(sort.stream()
                .map(order -> order.direction() == SortDirection.ASCENDING
                        ? builder.asc(resolvePath(dataRoot, order.property()))
                        : builder.desc(resolvePath(dataRoot, order.property())))
                .toList());

        List<T> items = List.copyOf(entityManager().createQuery(dataQuery)
                .setFirstResult(request.offset())
                .setMaxResults(request.limit())
                .getResultList());

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<T> countRoot = countQuery.from(entityClass());
        if (specification != null) {
            countQuery.where(specification.toPredicate(
                    countRoot, countQuery, builder));
        }
        countQuery.select(countQuery.isDistinct()
                ? builder.countDistinct(countRoot)
                : builder.count(countRoot));

        return new Page<>(
                items,
                entityManager().createQuery(countQuery).getSingleResult(),
                request.offset(),
                request.limit());
    }

    public long count() {
        var builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<T> root = query.from(entityClass());
        query.select(builder.count(root));
        return entityManager().createQuery(query).getSingleResult();
    }

    public void flush() {
        entityManager().flush();
    }

    public void detach(T entity) {
        entityManager().detach(Objects.requireNonNull(entity, "entity"));
    }

    public jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder() {
        return entityManager().getCriteriaBuilder();
    }

    public CriteriaQuery<T> criteriaQuery() {
        return criteriaBuilder().createQuery(entityClass());
    }

    public Root<T> root(CriteriaQuery<T> query) {
        return query.from(entityClass());
    }

    protected final Path<?> identifierPath(Root<T> root) {
        return root.get(identifierAttribute().getName());
    }

    private EntityType<T> entityType() {
        return entityManager().getMetamodel().entity(entityClass());
    }

    private Attribute<? super T, ?> identifierAttribute() {
        EntityType<T> type = entityType();
        return type.getId(type.getIdType().getJavaType());
    }

    private Path<?> resolvePath(Root<T> root, String property) {
        Objects.requireNonNull(property, "property");
        String[] segments = property.split("\\.", -1);
        Path<?> path = root;
        ManagedType<?> managedType = entityType();
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (segment.isBlank()) {
                throw new IllegalArgumentException(
                        "Property path contains an empty segment: " + property);
            }
            final Attribute<?, ?> attribute;
            try {
                attribute = managedType.getAttribute(segment);
            } catch (IllegalArgumentException missing) {
                throw new IllegalArgumentException(
                        "Unknown persistent attribute " + segment
                                + " in property path " + property,
                        missing);
            }
            path = path.get(segment);
            if (index < segments.length - 1) {
                if (!attribute.isAssociation()) {
                    throw new IllegalArgumentException(
                            "Non-association attribute " + segment
                                    + " cannot be traversed in " + property);
                }
                managedType = entityManager().getMetamodel()
                        .managedType(attribute.getJavaType());
            }
        }
        return path;
    }

    private I requireId(I id) {
        return Objects.requireNonNull(id, "id");
    }
}
