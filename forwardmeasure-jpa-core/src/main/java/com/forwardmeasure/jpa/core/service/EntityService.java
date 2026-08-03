package com.forwardmeasure.jpa.core.service;

import com.forwardmeasure.jpa.core.entity.AbstractBaseEntity;
import com.forwardmeasure.jpa.core.query.JpaSpecification;
import com.forwardmeasure.jpa.core.query.Page;
import com.forwardmeasure.jpa.core.query.PageRequest;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Framework-neutral application-facing persistence operations for a JPA
 * entity.
 *
 * <p>The service deliberately does not expose its repository. Applications
 * depend on this contract or a domain-specific extension, while repository
 * and entity-manager access remain implementation details.
 */
public interface EntityService<
        T extends AbstractBaseEntity<I>, I extends Serializable> {

    T save(T entity);

    T saveAndFlush(T entity);

    Optional<T> findById(I id);

    List<T> findAll();

    Page<T> findAll(PageRequest pageRequest);

    Page<T> findAll(
            PageRequest pageRequest,
            JpaSpecification<T> specification);

    long count();

    boolean deleteById(I id);

    void flush();

    void detach(T entity);
}
