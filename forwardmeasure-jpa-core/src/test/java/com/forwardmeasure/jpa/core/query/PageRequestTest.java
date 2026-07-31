package com.forwardmeasure.jpa.core.query;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PageRequestTest {

    @Test
    void rejectsUnboundedAndInvalidPages() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PageRequest(-1, 10, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PageRequest(0, 0, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PageRequest(
                        0, PageRequest.MAXIMUM_LIMIT + 1, null));
    }
}
