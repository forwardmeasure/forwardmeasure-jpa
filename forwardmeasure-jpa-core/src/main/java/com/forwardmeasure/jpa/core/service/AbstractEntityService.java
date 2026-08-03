package com.forwardmeasure.jpa.core.service;

import com.forwardmeasure.jpa.core.entity.AbstractBaseEntity;
import com.forwardmeasure.jpa.core.query.JpaSpecification;
import com.forwardmeasure.jpa.core.query.Page;
import com.forwardmeasure.jpa.core.query.PageRequest;
import com.forwardmeasure.jpa.core.repository.EntityRepository;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reusable service implementation backed by a provider-neutral repository.
 *
 * <p>The repository accessor is protected solely so a domain-specific service
 * implementation can add explicit queries. It is never exposed to callers.
 * Transaction boundaries are supplied by the consuming framework or the
 * enclosing application service.
 */
public abstract class AbstractEntityService<
        T extends AbstractBaseEntity<I>,
        I extends Serializable,
        R extends EntityRepository<T, I>>
        implements EntityService<T, I> {

    private final R repository;

    protected AbstractEntityService(R repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    protected final R repository() {
        return repository;
    }

    @Override
    public T save(T entity) {
        return repository.save(entity);
    }

    @Override
    public T saveAndFlush(T entity) {
        T saved = repository.save(entity);
        repository.flush();
        return saved;
    }

    @Override
    public Optional<T> findById(I id) {
        return repository.findById(id);
    }

    @Override
    public List<T> findAll() {
        return repository.findAll();
    }

    @Override
    public Page<T> findAll(PageRequest pageRequest) {
        return repository.findAll(pageRequest);
    }

    @Override
    public Page<T> findAll(
            PageRequest pageRequest,
            JpaSpecification<T> specification) {
        return repository.findAll(pageRequest, specification);
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public boolean deleteById(I id) {
        return repository.deleteById(id);
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public void detach(T entity) {
        repository.detach(entity);
    }
}
