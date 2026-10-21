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
package com.forwardmeasure.jpa.contract;

import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity;
import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity_;
import com.forwardmeasure.jpa.core.query.JpaSpecification;
import com.forwardmeasure.jpa.core.query.PageRequest;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.identity.service.OwnedEntityService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Shared service assertions executed by every supported framework. */
public final class JpaServiceContract {

  private JpaServiceContract() {}

  public static ContractResult verify(
      ActorService actors, OwnedEntityService<ContractOwnedEntity, Long> owned) {
    Objects.requireNonNull(actors, "actors");
    Objects.requireNonNull(owned, "owned");

    // identity.actor is shared by every schema in a tenant database: each run uses its own
    // identity so repeated or concurrent contract runs never collide.
    String run = UUID.randomUUID().toString();
    String serviceSubject = "service-contract-user-" + run;
    String serviceEmail = "service-contract-" + run + "@example.test";
    Actor actor = new Actor();
    actor.setSubjectIdentifier(serviceSubject);
    actor.setIdentityProvider("service-contract-idp");
    actor.setType(IdentityType.SERVICE);
    actor.setEmail(serviceEmail);
    actors.persistAndFlush(actor);

    ContractOwnedEntity entity = new ContractOwnedEntity();
    entity.setName("service-first");
    entity.setOwner(actor);
    owned.persistAndFlush(entity);

    require(actor.getId() != null, "Service did not persist actor");
    require(entity.getId() != null, "Service did not persist owned entity");
    require(actors.findByIdOptional(actor.getId()).isPresent(), "Actor service id lookup failed");
    require(actors.findByUuid(actor.getUuid()).isPresent(), "Actor service UUID lookup failed");
    require(
        actors.findByIdentity("service-contract-idp", serviceSubject).isPresent(),
        "Actor service identity lookup failed");
    require(
        actors.existsByIdentity("service-contract-idp", serviceSubject),
        "Actor service identity existence failed");
    require(actors.findByEmail(serviceEmail).size() == 1, "Actor service email lookup failed");
    List<Actor> services = actors.findByType(IdentityType.SERVICE);
    require(
        services.stream().anyMatch(found -> found.getId().equals(actor.getId()))
            && services.stream().allMatch(found -> found.getType() == IdentityType.SERVICE),
        "Actor service type lookup failed");
    require(owned.findByIdOptional(entity.getId()).isPresent(), "Owned service id lookup failed");
    require(owned.findByUuid(entity.getUuid()).isPresent(), "Owned service UUID lookup failed");
    require(
        owned.findByUuids(List.of(entity.getUuid())).size() == 1,
        "Owned service bulk UUID lookup failed");
    require(owned.existsByUuid(entity.getUuid()), "Owned service UUID existence failed");
    require(owned.findByOwnerId(actor.getId()).size() == 1, "Owned service owner lookup failed");
    require(owned.countByOwnerId(actor.getId()) == 1, "Owned service owner count failed");
    require(
        owned.existsByUuidAndOwnerSubjectIdentifier(entity.getUuid(), serviceSubject),
        "Owned service owner-subject lookup failed");

    var page =
        owned.page(
            new PageRequest(0, 10, List.of()),
            (JpaSpecification<ContractOwnedEntity>)
                (root, query, builder) ->
                    builder.equal(root.get(ContractOwnedEntity_.name), "service-first"));
    require(
        page.items().size() == 1 && page.totalItems() == 1, "Service specification paging failed");

    ContractOwnedEntity disposable = new ContractOwnedEntity();
    disposable.setName("service-disposable");
    disposable.setOwner(actor);
    owned.persistAndFlush(disposable);
    require(owned.deleteByUuid(disposable.getUuid()), "Service UUID deletion failed");
    owned.flush();
    require(!owned.existsByUuid(disposable.getUuid()), "Service deletion remained visible");

    return new ContractResult(
        actor.getId(), actor.getUuid(), entity.getId(), entity.getUuid(), entity.getVersion());
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
