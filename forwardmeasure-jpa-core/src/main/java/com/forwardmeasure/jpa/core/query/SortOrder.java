package com.forwardmeasure.jpa.core.query;

import java.util.Objects;

public record SortOrder(String property, SortDirection direction) {

    public SortOrder {
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(direction, "direction");
        if (property.isBlank()) {
            throw new IllegalArgumentException("property must not be blank");
        }
    }
}
