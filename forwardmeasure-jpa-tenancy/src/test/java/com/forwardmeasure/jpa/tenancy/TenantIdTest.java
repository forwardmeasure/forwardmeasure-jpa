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

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantIdTest {

  @Test
  void forDidDerivesTheSameUuidEveryTime() {
    Did did = Did.parse("did:web:lux.kriyagentic.com");

    TenantId first = TenantId.forDid(did);
    TenantId second = TenantId.forDid(did);

    assertEquals(first, second);
  }

  @Test
  void forDidMatchesTheKnownVerifiedDerivationForARealTenant() {
    // Independently computed (Python, replicating java.util.UUID#nameUUIDFromBytes exactly: MD5
    // over the UTF-8 bytes, then set the version/variant bits) - not just recalled, verified fresh.
    // Same value openworkflow-engine-api's own TenantId(String) legacy DID-fallback path would
    // produce for this exact DID, confirming both TenantId types derive identically.
    Did did = Did.parse("did:web:lux.kriyagentic.com");

    TenantId tenantId = TenantId.forDid(did);

    assertEquals(UUID.fromString("50b78292-e7b9-33ed-b6ad-8f9671278ff9"), tenantId.value());
  }

  @Test
  void differentDidsDeriveDifferentTenantIds() {
    TenantId first = TenantId.forDid(Did.parse("did:web:lux.kriyagentic.com"));
    TenantId second = TenantId.forDid(Did.parse("did:web:acme.kriyagentic.com"));

    org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
  }
}
