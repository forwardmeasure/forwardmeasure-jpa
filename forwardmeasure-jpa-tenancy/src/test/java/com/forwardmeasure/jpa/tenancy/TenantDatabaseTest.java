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
