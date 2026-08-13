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
}
