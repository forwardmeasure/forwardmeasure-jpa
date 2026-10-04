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

import java.util.List;

public record PageRequest(int offset, int limit, List<SortOrder> sort) {

  public static final int DEFAULT_LIMIT = 32;
  public static final int MAXIMUM_LIMIT = 1_000;

  public PageRequest {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative");
    }
    if (limit < 1 || limit > MAXIMUM_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + MAXIMUM_LIMIT);
    }
    sort = sort == null ? List.of() : List.copyOf(sort);
  }

  public static PageRequest firstPage() {
    return new PageRequest(0, DEFAULT_LIMIT, List.of());
  }
}
