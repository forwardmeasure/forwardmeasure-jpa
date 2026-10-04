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
package com.forwardmeasure.jpa.identity.service.impl;

import com.forwardmeasure.jpa.core.service.impl.AuditedEntityServiceImpl;
import com.forwardmeasure.jpa.identity.entity.OwnedEntity;
import com.forwardmeasure.jpa.identity.repository.AbstractOwnedEntityRepository;
import com.forwardmeasure.jpa.identity.service.OwnedEntityService;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Standard-JPA service base for actor-owned entities. */
public class OwnedEntityServiceImpl<
        T extends OwnedEntity<I>,
        I extends Serializable,
        R extends AbstractOwnedEntityRepository<T, I>>
    extends AuditedEntityServiceImpl<T, I, R> implements OwnedEntityService<T, I> {

  public OwnedEntityServiceImpl(R repository) {
    super(repository);
  }

  @Override
  public List<T> findByOwnerId(Long ownerId) {
    return repository().findByOwnerId(ownerId);
  }

  @Override
  public List<T> findByOwnerSubjectIdentifier(String subjectIdentifier) {
    return repository().findByOwnerSubjectIdentifier(subjectIdentifier);
  }

  @Override
  public Optional<String> findOwnerSubjectIdentifierById(I id) {
    return repository().findOwnerSubjectIdentifierById(id);
  }

  @Override
  public Optional<String> findOwnerSubjectIdentifierByUuid(UUID uuid) {
    return repository().findOwnerSubjectIdentifierByUuid(uuid);
  }

  @Override
  public long countByOwnerId(Long ownerId) {
    return repository().countByOwnerId(ownerId);
  }

  @Override
  public boolean existsByIdAndOwnerId(I id, Long ownerId) {
    return repository().existsByIdAndOwnerId(id, ownerId);
  }

  @Override
  public boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId) {
    return repository().existsByUuidAndOwnerId(uuid, ownerId);
  }

  @Override
  public boolean existsByIdAndOwnerSubjectIdentifier(I id, String subjectIdentifier) {
    return repository().existsByIdAndOwnerSubjectIdentifier(id, subjectIdentifier);
  }

  @Override
  public boolean existsByUuidAndOwnerSubjectIdentifier(UUID uuid, String subjectIdentifier) {
    return repository().existsByUuidAndOwnerSubjectIdentifier(uuid, subjectIdentifier);
  }
}
