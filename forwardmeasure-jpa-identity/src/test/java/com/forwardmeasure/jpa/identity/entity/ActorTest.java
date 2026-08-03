package com.forwardmeasure.jpa.identity.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ActorTest {

    @Test
    void actorHasIndependentStableIdentity() {
        Actor actor = Actor.builder()
                .subjectIdentifier("actor-subject")
                .type(IdentityType.HUMAN)
                .build();
        actor.initializeIdentity();

        assertNotNull(actor.getUuid());
        assertEquals("actor-subject", actor.getSubjectIdentifier());
    }

    @Test
    void parsesIdentityTypeStrictly() {
        assertEquals(IdentityType.HUMAN, IdentityType.fromCode("human"));
        assertThrows(
                IllegalArgumentException.class,
                () -> IdentityType.fromCode("unknown"));
    }
}
