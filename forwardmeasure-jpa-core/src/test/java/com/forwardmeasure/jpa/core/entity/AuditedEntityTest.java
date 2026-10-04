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
