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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ActorDidsTest {

  private static final String SUBJECT = "2f6c0a1e-6f0b-4c1a-9d2e-4b7f2c9a1d33";

  @Test
  void synthesisesTheActorDidFromProviderAndSubject() {
    assertEquals(
        "did:web:kriyagentic.com:actors:keycloak:" + SUBJECT,
        ActorDids.of("kriyagentic.com", "keycloak", SUBJECT));
    assertEquals(
        "did:web:kriyagentic.com:actors:openworkflow-runtime:human-task-service",
        ActorDids.of("kriyagentic.com", "openworkflow-runtime", "human-task-service"));
  }

  @Test
  void usesTheActorsOwnIdentity() {
    Actor actor =
        Actor.builder()
            .identityProvider("keycloak")
            .subjectIdentifier(SUBJECT)
            .type(IdentityType.HUMAN)
            .build();
    assertEquals(
        "did:web:kriyagentic.com:actors:keycloak:" + SUBJECT,
        ActorDids.of("kriyagentic.com", actor));
  }

  @Test
  void percentEncodesWhatADidSegmentCannotCarryAndRoundTrips() {
    String did = ActorDids.of("localhost:8443", "test idp", "user:42@example");
    assertEquals("did:web:localhost%3A8443:actors:test%20idp:user%3A42%40example", did);
    assertEquals(
        new ActorDids.ActorIdentity("test idp", "user:42@example"),
        ActorDids.parse("localhost:8443", did));
  }

  @Test
  void parsesBackTheIdentity() {
    assertEquals(
        new ActorDids.ActorIdentity("keycloak", SUBJECT),
        ActorDids.parse("kriyagentic.com", ActorDids.of("kriyagentic.com", "keycloak", SUBJECT)));
  }

  @Test
  void refusesAnythingThatIsNotAnActorDidUnderTheDomain() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ActorDids.parse("kriyagentic.com", "did:web:other.com:actors:keycloak:" + SUBJECT));
    assertThrows(
        IllegalArgumentException.class,
        () -> ActorDids.parse("kriyagentic.com", "did:web:kriyagentic.com:actors:keycloak"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ActorDids.parse("kriyagentic.com", "did:web:kriyagentic.com:tenants:lux"));
    assertThrows(IllegalArgumentException.class, () -> ActorDids.of("", "keycloak", SUBJECT));
    assertThrows(
        IllegalArgumentException.class, () -> ActorDids.of("kriyagentic.com", "keycloak", " "));
  }
}
