package com.forwardmeasure.jpa.core.query;

import java.util.List;
import java.util.Objects;

public record Page<T>(
        List<T> items,
        long totalItems,
        int offset,
        int limit) {

    public Page {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (totalItems < 0 || offset < 0 || limit < 1) {
            throw new IllegalArgumentException(
                    "Invalid page totals or boundaries");
        }
    }
}
