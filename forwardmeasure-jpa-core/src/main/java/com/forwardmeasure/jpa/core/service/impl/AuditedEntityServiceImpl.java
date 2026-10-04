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
package com.forwardmeasure.jpa.core.service.impl;

import com.forwardmeasure.jpa.core.entity.AuditedEntity;
import com.forwardmeasure.jpa.core.repository.AbstractAuditedEntityRepository;
import com.forwardmeasure.jpa.core.service.AuditedEntityService;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AuditedEntityServiceImpl<
        T extends AuditedEntity<I>,
        I extends Serializable,
        R extends AbstractAuditedEntityRepository<T, I>>
    extends AbstractBaseServiceImpl<T, I, R> implements AuditedEntityService<T, I> {

  protected AuditedEntityServiceImpl(R repository) {
    super(repository);
  }

  @Override
  public Optional<T> findByUuid(UUID uuid) {
    return repository().findByUuid(uuid);
  }

  @Override
  public List<T> findByUuids(Collection<UUID> uuids) {
    return repository().findByUuids(uuids);
  }

  @Override
  public boolean existsByUuid(UUID uuid) {
    return repository().existsByUuid(uuid);
  }

  @Override
  public boolean deleteByUuid(UUID uuid) {
    return repository().deleteByUuid(uuid);
  }
}
