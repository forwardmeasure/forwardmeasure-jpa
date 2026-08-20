package com.forwardmeasure.jpa.core.service;

import com.forwardmeasure.jpa.core.entity.AbstractBaseEntity;
import com.forwardmeasure.jpa.core.query.JpaSpecification;
import com.forwardmeasure.jpa.core.query.Page;
import com.forwardmeasure.jpa.core.query.PageRequest;
import jakarta.persistence.LockModeType;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public interface AbstractBaseService<T extends AbstractBaseEntity<I>, I extends Serializable> {

  void persist(T entity);

  void persistAndFlush(T entity);

  T merge(T entity);

  void delete(T entity);

  boolean isPersistent(T entity);

  T findById(I id);

  T findById(I id, LockModeType lockMode);

  Optional<T> findByIdOptional(I id);

  Optional<T> findByIdOptional(I id, LockModeType lockMode);

  List<T> listAll();

  Stream<T> streamAll();

  Page<T> page(PageRequest request);

  Page<T> page(PageRequest request, JpaSpecification<T> specification);

  long count();

  void persist(Iterable<T> entities);

  void persist(T first, T... remaining);

  long deleteAll();

  boolean deleteById(I id);

  void flush();

  void detach(T entity);
}
