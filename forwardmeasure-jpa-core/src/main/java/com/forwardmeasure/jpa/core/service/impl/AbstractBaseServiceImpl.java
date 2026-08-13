package com.forwardmeasure.jpa.core.service.impl;

import com.forwardmeasure.jpa.core.entity.AbstractBaseEntity;
import com.forwardmeasure.jpa.core.query.JpaSpecification;
import com.forwardmeasure.jpa.core.query.Page;
import com.forwardmeasure.jpa.core.query.PageRequest;
import com.forwardmeasure.jpa.core.repository.AbstractBaseRepository;
import com.forwardmeasure.jpa.core.service.AbstractBaseService;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Transactional
public class AbstractBaseServiceImpl<
        T extends AbstractBaseEntity<I>,
        I extends Serializable,
        R extends AbstractBaseRepository<T, I>>
        implements AbstractBaseService<T, I> {

    private final R repository;

    protected AbstractBaseServiceImpl(R repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    protected final R repository() {
        return repository;
    }

    @Override
    public void persist(T entity) {
        repository.persist(entity);
    }

    @Override
    public void persistAndFlush(T entity) {
        repository.persistAndFlush(entity);
    }

    @Override
    public T merge(T entity) {
        return repository.merge(entity);
    }

    @Override
    public void delete(T entity) {
        repository.delete(entity);
    }

    @Override
    public boolean isPersistent(T entity) {
        return repository.isPersistent(entity);
    }

    @Override
    public T findById(I id) {
        return repository.findById(id);
    }

    @Override
    public T findById(I id, LockModeType lockMode) {
        return repository.findById(id, lockMode);
    }

    @Override
    public Optional<T> findByIdOptional(I id) {
        return repository.findByIdOptional(id);
    }

    @Override
    public Optional<T> findByIdOptional(I id, LockModeType lockMode) {
        return repository.findByIdOptional(id, lockMode);
    }

    @Override
    public List<T> listAll() {
        return repository.listAll();
    }

    @Override
    public Stream<T> streamAll() {
        return repository.listAll().stream();
    }

    @Override
    public Page<T> page(PageRequest request) {
        return repository.page(request);
    }

    @Override
    public Page<T> page(
            PageRequest request, JpaSpecification<T> specification) {
        return repository.page(request, specification);
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public void persist(Iterable<T> entities) {
        repository.persist(entities);
    }

    @Override
    @SuppressWarnings({"unchecked", "varargs"})
    public void persist(T first, T... remaining) {
        repository.persist(first, remaining);
    }

    @Override
    public long deleteAll() {
        return repository.deleteAll();
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
