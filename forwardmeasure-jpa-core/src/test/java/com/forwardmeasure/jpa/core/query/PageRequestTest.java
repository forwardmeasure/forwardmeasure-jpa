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
package com.forwardmeasure.jpa.core.query;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PageRequestTest {

  @Test
  void rejectsUnboundedAndInvalidPages() {
    assertThrows(IllegalArgumentException.class, () -> new PageRequest(-1, 10, null));
    assertThrows(IllegalArgumentException.class, () -> new PageRequest(0, 0, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PageRequest(0, PageRequest.MAXIMUM_LIMIT + 1, null));
  }

  @Test
  void copiesCollectionsAndRejectsInvalidResultBoundaries() {
    var items = new java.util.ArrayList<>(java.util.List.of("one"));
    var page = new Page<>(items, 1L, 0, 1);
    items.clear();
    org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("one"), page.items());
    assertThrows(UnsupportedOperationException.class, () -> page.items().clear());
    assertThrows(IllegalArgumentException.class, () -> new Page<>(java.util.List.of(), -1L, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> new Page<>(java.util.List.of(), 0L, -1, 1));
    assertThrows(IllegalArgumentException.class, () -> new Page<>(java.util.List.of(), 0L, 0, 0));
    assertThrows(NullPointerException.class, () -> new Page<>(null, 0L, 0, 1));
    org.junit.jupiter.api.Assertions.assertEquals(
        java.util.List.of(), new PageRequest(0, 1, null).sort());
    assertThrows(IllegalArgumentException.class, () -> new SortOrder(" ", SortDirection.ASCENDING));
  }
}
