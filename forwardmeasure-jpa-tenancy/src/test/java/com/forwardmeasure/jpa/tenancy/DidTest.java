package com.forwardmeasure.jpa.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DidTest {

  @Test
  void parsesMethodAndMethodSpecificId() {
    Did did = Did.parse("did:web:lux.kriyagentic.com");

    assertEquals("web", did.method());
    assertEquals("lux.kriyagentic.com", did.methodSpecificId());
    assertEquals("did:web:lux.kriyagentic.com", did.toString());
  }

  @Test
  void parsesAMultiSegmentMethodSpecificId() {
    Did did = Did.parse("did:forwardmeasure:tenant:c0d34a2f-387e-4c89-9b84-75c5bbff9a61");

    assertEquals("forwardmeasure", did.method());
    assertEquals("tenant:c0d34a2f-387e-4c89-9b84-75c5bbff9a61", did.methodSpecificId());
  }

  @Test
  void rejectsTextThatDoesNotConformToDidSyntax() {
    assertThrows(IllegalArgumentException.class, () -> new Did("not-a-did"));
    assertThrows(IllegalArgumentException.class, () -> new Did("did:"));
    assertThrows(IllegalArgumentException.class, () -> new Did("did:web:"));
    assertThrows(IllegalArgumentException.class, () -> new Did("DID:web:example.com"));
  }
}
