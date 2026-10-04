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
package com.forwardmeasure.jpa.identity.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActorTest {

  @Test
  void actorHasIndependentStableIdentity() {
    Actor actor =
        Actor.builder().subjectIdentifier("actor-subject").type(IdentityType.HUMAN).build();
    actor.initializeIdentity();

    assertNotNull(actor.getUuid());
    assertEquals("actor-subject", actor.getSubjectIdentifier());
  }

  @Test
  void actorPreservesAnExplicitStableIdentity() {
    UUID identity = UUID.randomUUID();
    Actor actor =
        Actor.builder()
            .uuid(identity)
            .subjectIdentifier("actor-subject")
            .type(IdentityType.HUMAN)
            .build();

    actor.initializeIdentity();

    assertEquals(identity, actor.getUuid());
  }

  @Test
  void parsesIdentityTypeStrictly() {
    assertEquals(IdentityType.HUMAN, IdentityType.fromCode("human"));
    assertThrows(IllegalArgumentException.class, () -> IdentityType.fromCode(null));
    assertThrows(IllegalArgumentException.class, () -> IdentityType.fromCode(" "));
    assertThrows(IllegalArgumentException.class, () -> IdentityType.fromCode("unknown"));
  }

  @Test
  void ownedEntityProjectsItsOptionalOwnerIdentity() {
    TestOwnedEntity entity = new TestOwnedEntity();
    assertTrue(entity.getOwnerSubjectIdentifier().isEmpty());
    assertFalse(entity.hasOwner());

    entity.setOwner(
        Actor.builder().subjectIdentifier("owner-subject").type(IdentityType.HUMAN).build());

    assertEquals("owner-subject", entity.getOwnerSubjectIdentifier().orElseThrow());
    assertTrue(entity.hasOwner());
  }

  private static final class TestOwnedEntity extends OwnedEntity<Long> {

    private static final long serialVersionUID = 1L;

    private Long id;

    @Override
    public Long getId() {
      return id;
    }

    @Override
    public void setId(Long id) {
      this.id = id;
    }
  }
}
