package com.forwardmeasure.jpa.core.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AuditedEntityTest {

  @Test
  void initializesStableIdentityAndUtcTimestamps() {
    TestEntity entity = new TestEntity();

    entity.initializeAuditFields();

    assertNotNull(entity.getUuid());
    assertNotNull(entity.getCreatedAt());
    assertNotNull(entity.getUpdatedAt());
    assertEquals(0, entity.getCreatedAt().getOffset().getTotalSeconds());
  }

  @Test
  void preservesExplicitValuesAtCreation() {
    TestEntity entity = new TestEntity();
    OffsetDateTime timestamp = OffsetDateTime.parse("2026-01-02T03:04:05Z");
    entity.setCreatedAt(timestamp);
    entity.setUpdatedAt(timestamp);

    entity.initializeAuditFields();

    assertEquals(timestamp, entity.getCreatedAt());
    assertEquals(timestamp, entity.getUpdatedAt());
  }

  private static final class TestEntity extends AuditedEntity<Long> {
    private Long id;

    @Override
    public Long getId() {
      return id;
    }

    @Override
    public void setId(Long id) {
      this.id = id;
    }
  }
}
