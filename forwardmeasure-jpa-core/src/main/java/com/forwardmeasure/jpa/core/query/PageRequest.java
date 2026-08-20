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
