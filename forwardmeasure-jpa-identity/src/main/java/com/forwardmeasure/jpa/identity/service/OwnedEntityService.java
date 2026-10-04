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
package com.forwardmeasure.jpa.identity.service;

import com.forwardmeasure.jpa.core.service.AuditedEntityService;
import com.forwardmeasure.jpa.identity.entity.OwnedEntity;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application-facing persistence operations for actor-owned entities. */
public interface OwnedEntityService<T extends OwnedEntity<I>, I extends Serializable>
    extends AuditedEntityService<T, I> {

  List<T> findByOwnerId(Long ownerId);

  List<T> findByOwnerSubjectIdentifier(String subjectIdentifier);

  Optional<String> findOwnerSubjectIdentifierById(I id);

  Optional<String> findOwnerSubjectIdentifierByUuid(UUID uuid);

  long countByOwnerId(Long ownerId);

  boolean existsByIdAndOwnerId(I id, Long ownerId);

  boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId);

  boolean existsByIdAndOwnerSubjectIdentifier(I id, String subjectIdentifier);

  boolean existsByUuidAndOwnerSubjectIdentifier(UUID uuid, String subjectIdentifier);
}
