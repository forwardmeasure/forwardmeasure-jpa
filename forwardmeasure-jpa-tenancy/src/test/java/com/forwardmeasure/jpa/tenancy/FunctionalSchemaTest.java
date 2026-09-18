package com.forwardmeasure.jpa.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FunctionalSchemaTest {

  @Test
  void schemaNamesMatchProductConvention() {
    assertEquals("openworkflow", FunctionalSchema.OPENWORKFLOW.schemaName());
    assertEquals("entity_intelligence", FunctionalSchema.ENTITY_INTELLIGENCE.schemaName());
    assertEquals("decision_intelligence", FunctionalSchema.DECISION_INTELLIGENCE.schemaName());
    assertEquals("agent_os", FunctionalSchema.AGENT_OS.schemaName());
    assertEquals("data_streaming", FunctionalSchema.DATA_STREAMING.schemaName());
  }

  @Test
  void toStringMatchesSchemaName() {
    for (FunctionalSchema schema : FunctionalSchema.values()) {
      assertEquals(schema.schemaName(), schema.toString());
    }
  }
}
