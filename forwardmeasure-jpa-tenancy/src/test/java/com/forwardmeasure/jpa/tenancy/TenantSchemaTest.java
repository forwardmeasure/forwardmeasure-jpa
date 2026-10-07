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

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantSchemaTest {
  @Test
  void roundTripsTenantIdsAndRejectsBootstrapAndUnsafeIdentifiers() {
    var tenant = new TenantId(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));
    var schema = TenantSchema.forTenant(tenant);
    assertEquals(tenant, schema.tenantId());
    assertEquals("t_0123456789abcdef0123456789abcdef", schema.value());
    assertEquals(schema.value(), schema.toString());
    assertEquals(schema, new TenantSchema("T_0123456789ABCDEF0123456789ABCDEF"));
    assertEquals(TenantSchema.PUBLIC, new TenantSchema("PUBLIC"));
    assertThrows(IllegalStateException.class, TenantSchema.PUBLIC::tenantId);
    assertThrows(
        IllegalArgumentException.class, () -> new TenantSchema(TenantSchema.UNBOUND_IDENTIFIER));
    assertThrows(
        IllegalArgumentException.class, () -> new TenantSchema("public; DROP TABLE actor"));
    assertThrows(NullPointerException.class, () -> new TenantSchema(null));
  }
}
