package com.forwardmeasure.jpa.core.repository;

import com.forwardmeasure.jpa.core.entity.AbstractBaseEntity;
import com.forwardmeasure.jpa.core.query.JpaSpecification;
import com.forwardmeasure.jpa.core.query.Page;
import com.forwardmeasure.jpa.core.query.PageRequest;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Framework-neutral repository contract implemented by each supported
 * integration. It intentionally exposes no Panache, Spring Data, or Micronaut
 * types.
 */
public interface EntityRepository<
        T extends AbstractBaseEntity<I>, I extends Serializable> {

    T save(T entity);

    Optional<T> findById(I id);

    List<T> findAll();

    Page<T> findAll(PageRequest pageRequest);

    Page<T> findAll(
            PageRequest pageRequest, JpaSpecification<T> specification);

    long count();

    boolean deleteById(I id);

    void flush();

    void detach(T entity);
}
