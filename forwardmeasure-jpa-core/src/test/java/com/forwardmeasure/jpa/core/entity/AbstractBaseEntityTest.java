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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class AbstractBaseEntityTest {

  @Test
  void comparesOnlyNonTransientEntitiesOfTheExactSameType() {
    TestEntity first = new TestEntity();
    TestEntity second = new TestEntity();

    assertFalse(first.equals(second));
    assertEquals(first, first);

    first.setId(42L);
    second.setId(42L);

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertNotEquals(first, new OtherEntity(42L));
  }

  @Test
  void usesZeroHashForAnEntityWithoutAnIdentifier() {
    assertEquals(0, new TestEntity().hashCode());
  }

  private static final class TestEntity extends AbstractBaseEntity<Long> {

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

  private static final class OtherEntity extends AbstractBaseEntity<Long> {

    private Long id;

    private OtherEntity(Long id) {
      this.id = id;
    }

    @Override
    public Long getId() {
      return id;
    }

    @Override
    public void setId(Long id) {
      this.id = id;
    }
  }

  @Test
  void distinguishesNullTransientAndDifferentPersistentIdentifiers() {
    TestEntity persisted = new TestEntity();
    persisted.setId(42L);
    TestEntity transientEntity = new TestEntity();
    assertNotEquals(persisted, null);
    assertNotEquals(persisted, transientEntity);
    assertNotEquals(transientEntity, persisted);
    TestEntity different = new TestEntity();
    different.setId(43L);
    assertNotEquals(persisted, different);
  }
}
