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

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformDidsTest {

  private static final PlatformDids DIDS = new PlatformDids("fwm");

  @Test
  void mintsBundleAndEntityDidsWithTheConfiguredMethod() {
    UUID tenant = UUID.fromString("c0d34a2f-387e-4c89-9b84-75c5bbff9a61");
    UUID subject = UUID.fromString("0b7e8a8e-9d5c-4f0e-8f53-2b1d6b1c7e11");

    assertEquals("did:fwm:bundle:fei", DIDS.bundle("fei").value());
    assertEquals(
        "did:fwm:bundle:fei/asyncapi/worker.yaml",
        DIDS.bundleResource("fei", "/asyncapi/worker.yaml").toString());
    assertEquals("did:fwm:entity:" + tenant + ":" + subject, DIDS.entity(tenant, subject).value());
    assertEquals(
        "did:kriyagentic:bundle:fei", new PlatformDids("kriyagentic").bundle("fei").value());
  }

  @Test
  void rendersTheBundleDidTokenWithTheMethodAndDomain() {
    String definition = "endpoint: @BUNDLE_DID@/worker.yaml\nother: @BUNDLE_DID@/api.yaml\n";

    assertEquals(
        "endpoint: did:fwm:bundle:fei/worker.yaml\nother: did:fwm:bundle:fei/api.yaml\n",
        DIDS.renderBundleDid(definition, "fei"));
    assertEquals("no token here", DIDS.renderBundleDid("no token here", "fei"));
  }

  @Test
  void readsTheBundleDomainOnlyFromThisPlatformsBundleDids() {
    assertEquals(Optional.of("fei"), DIDS.bundleDomain(Did.parse("did:fwm:bundle:fei")));
    assertEquals(Optional.empty(), DIDS.bundleDomain(Did.parse("did:other:bundle:fei")));
    assertEquals(Optional.empty(), DIDS.bundleDomain(Did.parse("did:fwm:entity:fei")));
    assertEquals(Optional.empty(), DIDS.bundleDomain(Did.parse("did:fwm:bundle:fei:extra")));
  }

  @Test
  void rejectsInvalidMethodsAndDomains() {
    assertThrows(IllegalArgumentException.class, () -> new PlatformDids("FWM"));
    assertThrows(IllegalArgumentException.class, () -> new PlatformDids("f-w-m"));
    assertThrows(IllegalArgumentException.class, () -> DIDS.bundle("fei:x"));
    assertThrows(IllegalArgumentException.class, () -> DIDS.bundle(""));
  }
}
