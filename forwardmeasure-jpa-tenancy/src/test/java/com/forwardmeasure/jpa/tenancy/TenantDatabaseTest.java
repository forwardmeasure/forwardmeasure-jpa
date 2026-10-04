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

class TenantDatabaseTest {

  @Test
  void derivesFromAlias() {
    TenantDatabase database = TenantDatabase.forAlias("lux");

    assertEquals("forwardmeasure_lux", database.value());
    assertEquals("lux", database.alias());
  }

  @Test
  void allowsHyphenatedAliases() {
    TenantDatabase database = TenantDatabase.forAlias("acme-corp");

    assertEquals("forwardmeasure_acme-corp", database.value());
    assertEquals("acme-corp", database.alias());
  }

  @Test
  void normalizesToLowercase() {
    TenantDatabase database = new TenantDatabase("FORWARDMEASURE_LUX");

    assertEquals("forwardmeasure_lux", database.value());
  }

  @Test
  void neverNamesThePlatformControlPlaneDatabase() {
    assertThrows(IllegalArgumentException.class, () -> TenantDatabase.forAlias("control_plane"));
    assertThrows(
        IllegalArgumentException.class, () -> new TenantDatabase("FORWARDMEASURE_CONTROL_PLANE"));
    assertEquals("forwardmeasure_control-plane", TenantDatabase.forAlias("control-plane").value());
  }

  @Test
  void rejectsArbitraryDatabaseText() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TenantDatabase("forwardmeasure_x; drop database forwardmeasure_x"));
    assertThrows(IllegalArgumentException.class, () -> new TenantDatabase("public"));
    assertThrows(IllegalArgumentException.class, () -> new TenantDatabase("openworkflow"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TenantDatabase("tenant_792a6af3921b4951bd196c4ac82e701c"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TenantDatabase("t_792a6af3921b4951bd196c4ac82e701c"));
    assertThrows(IllegalArgumentException.class, () -> TenantDatabase.forAlias("lux\" or 1=1"));
  }
}
