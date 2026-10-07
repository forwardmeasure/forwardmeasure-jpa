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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DidUrlTest {

  private static final DidUrl BASE = DidUrl.parse("did:fwm:bundle:fei/asyncapi/worker.yaml");

  @Test
  void parsesTheDidAndThePath() {
    assertEquals("did:fwm:bundle:fei", BASE.did().value());
    assertEquals("/asyncapi/worker.yaml", BASE.path());
    assertEquals("did:fwm:bundle:fei/asyncapi/worker.yaml", BASE.toString());
  }

  @Test
  void rejectsWhatIsNotADidUrlWithANormalizedPath() {
    assertThrows(IllegalArgumentException.class, () -> DidUrl.parse("did:fwm:bundle:fei"));
    assertThrows(IllegalArgumentException.class, () -> DidUrl.parse("did:fwm:bundle:fei/"));
    assertThrows(IllegalArgumentException.class, () -> DidUrl.parse("did:fwm:bundle:fei/a//b"));
    assertThrows(IllegalArgumentException.class, () -> DidUrl.parse("did:fwm:bundle:fei/a/../b"));
    assertThrows(IllegalArgumentException.class, () -> DidUrl.parse("did:fwm:bundle:fei/a?x=1"));
    assertThrows(IllegalArgumentException.class, () -> DidUrl.parse("did:fwm:bundle:fei/a#f"));
    assertThrows(IllegalArgumentException.class, () -> DidUrl.parse("did:FWM:bundle:fei/a"));
  }

  @Test
  void resolvesReferencesAsRfc3986DoesForAHierarchicalPath() {
    assertEquals("did:fwm:bundle:fei/asyncapi/common.yaml", BASE.resolve("common.yaml").toString());
    assertEquals(
        "did:fwm:bundle:fei/asyncapi/schemas/a.yaml", BASE.resolve("./schemas/a.yaml").toString());
    assertEquals(
        "did:fwm:bundle:fei/openapi/api.yaml", BASE.resolve("../openapi/api.yaml").toString());
    assertEquals("did:fwm:bundle:fei/root.yaml", BASE.resolve("/root.yaml").toString());
    assertEquals(
        "did:fwm:bundle:other/x.yaml", BASE.resolve("did:fwm:bundle:other/x.yaml").toString());
  }

  @Test
  void rejectsReferencesThatLeaveTheBundleOrNameAnotherScheme() {
    assertThrows(IllegalArgumentException.class, () -> BASE.resolve("../../escape.yaml"));
    assertThrows(IllegalArgumentException.class, () -> BASE.resolve("https://example.com/a.yaml"));
    assertThrows(IllegalArgumentException.class, () -> BASE.resolve("common.yaml#/components"));
  }

  @Test
  void recognisesDidUrlText() {
    assertTrue(DidUrl.isDidUrl("did:fwm:bundle:fei/a.yaml"));
    assertFalse(DidUrl.isDidUrl("https://example.com/a.yaml"));
    assertFalse(DidUrl.isDidUrl(null));
  }

  @Test
  void rejectsEmptyResolutionAndSchemeOnlyReferences() {
    assertThrows(IllegalArgumentException.class, () -> BASE.resolve("/"));
    assertThrows(IllegalArgumentException.class, () -> BASE.resolve(".."));
    assertThrows(IllegalArgumentException.class, () -> BASE.resolve("mailto:person@example.test"));
    assertThrows(IllegalArgumentException.class, () -> BASE.resolve("common.yaml?format=json"));
    assertThrows(IllegalArgumentException.class, () -> DidUrl.parse("did:fwm:bundle:fei/a/./b"));
    assertEquals(
        "did:fwm:bundle:fei/asyncapi/folder/item:one.yaml",
        BASE.resolve("folder/item:one.yaml").toString());
    assertEquals(
        "did:fwm:bundle:fei/asyncapi/common.yaml", BASE.resolve("././common.yaml").toString());
    assertEquals(
        "did:fwm:bundle:fei/asyncapi/common.yaml",
        BASE.resolve("./folder//../common.yaml").toString());
  }
}
