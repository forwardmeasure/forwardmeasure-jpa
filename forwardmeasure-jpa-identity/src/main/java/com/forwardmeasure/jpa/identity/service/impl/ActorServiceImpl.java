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

import com.forwardmeasure.jpa.core.service.impl.AbstractBaseServiceImpl;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Standard-JPA actor service implementation shared by every application host. */
@Singleton
public class ActorServiceImpl extends AbstractBaseServiceImpl<Actor, Long, ActorRepository>
    implements ActorService {

  @Inject
  public ActorServiceImpl(ActorRepository repository) {
    super(repository);
  }

  @Override
  public Optional<Actor> findByUuid(UUID uuid) {
    return repository().findByUuid(uuid);
  }

  @Override
  public Optional<Actor> findByIdentity(String identityProvider, String subjectIdentifier) {
    return repository().findByIdentity(identityProvider, subjectIdentifier);
  }

  @Override
  public List<Actor> findByEmail(String email) {
    return repository().findByEmail(email);
  }

  @Override
  public List<Actor> findByType(IdentityType type) {
    return repository().findByType(type);
  }

  @Override
  public boolean existsByIdentity(String identityProvider, String subjectIdentifier) {
    return repository().existsByIdentity(identityProvider, subjectIdentifier);
  }
}
