package com.forwardmeasure.jpa.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ActorTest {

    @Test
    void actorHasIndependentStableIdentity() {
        Actor actor = new Actor();
        actor.initializeIdentity();

        assertNotNull(actor.getUuid());
        assertEquals("Actor", actor.getResourceType());
    }

    @Test
    void parsesIdentityTypeStrictly() {
        assertEquals(IdentityType.HUMAN, IdentityType.fromCode("human"));
        assertThrows(
                IllegalArgumentException.class,
                () -> IdentityType.fromCode("unknown"));
    }
}
