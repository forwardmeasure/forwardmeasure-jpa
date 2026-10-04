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
