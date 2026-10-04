/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.forwardmeasure.jpa.core.repository;

import com.forwardmeasure.jpa.core.entity.AuditedEntity;
import com.forwardmeasure.jpa.core.entity.AuditedEntity_;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Standard-JPA repository base for entities carrying an immutable UUID. */
public abstract class AbstractAuditedEntityRepository<
        T extends AuditedEntity<I>, I extends Serializable>
    extends AbstractBaseRepository<T, I> {

  public Optional<T> findByUuid(UUID uuid) {
    return findByUuid(uuid, null);
  }

  protected Optional<T> findByUuid(UUID uuid, LockModeType lockMode) {
    Objects.requireNonNull(uuid, "uuid");
    var builder = criteriaBuilder();
    CriteriaQuery<T> query = builder.createQuery(entityClass());
    Root<T> root = query.from(entityClass());
    query.select(root).where(builder.equal(root.get(AuditedEntity_.uuid), uuid));
    var typedQuery = entityManager().createQuery(query);
    if (lockMode != null) {
      typedQuery.setLockMode(lockMode);
    }
    return typedQuery.setMaxResults(1).getResultList().stream().findFirst();
  }

  public List<T> findByUuids(Collection<UUID> uuids) {
    Objects.requireNonNull(uuids, "uuids");
    if (uuids.isEmpty()) {
      return List.of();
    }
    var builder = criteriaBuilder();
    CriteriaQuery<T> query = builder.createQuery(entityClass());
    Root<T> root = query.from(entityClass());
    query.select(root).where(root.get(AuditedEntity_.uuid).in(List.copyOf(uuids)));
    return List.copyOf(entityManager().createQuery(query).getResultList());
  }

  public boolean existsByUuid(UUID uuid) {
    Objects.requireNonNull(uuid, "uuid");
    var builder = criteriaBuilder();
    CriteriaQuery<Long> query = builder.createQuery(Long.class);
    Root<T> root = query.from(entityClass());
    query.select(builder.count(root)).where(builder.equal(root.get(AuditedEntity_.uuid), uuid));
    return entityManager().createQuery(query).getSingleResult() > 0L;
  }

  public boolean deleteByUuid(UUID uuid) {
    Objects.requireNonNull(uuid, "uuid");
    var builder = criteriaBuilder();
    CriteriaDelete<T> delete = builder.createCriteriaDelete(entityClass());
    Root<T> root = delete.from(entityClass());
    delete.where(builder.equal(root.get(AuditedEntity_.uuid), uuid));
    return entityManager().createQuery(delete).executeUpdate() > 0;
  }
}
