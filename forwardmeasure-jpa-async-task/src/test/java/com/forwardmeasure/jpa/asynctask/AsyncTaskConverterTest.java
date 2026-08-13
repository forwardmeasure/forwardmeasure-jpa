package com.forwardmeasure.jpa.asynctask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forwardmeasure.jpa.asynctask.converter.AsyncTaskStatusConverter;
import com.forwardmeasure.jpa.asynctask.converter.AsyncTaskTypeConverter;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskStatus;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskType;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskTypeDefinition;
import org.junit.jupiter.api.Test;

class AsyncTaskConverterTest {

    private final AsyncTaskStatusConverter statuses =
            new AsyncTaskStatusConverter();

    private final AsyncTaskTypeConverter types =
            new AsyncTaskTypeConverter();

    @Test
    void convertsEveryStatusInBothDirections() {
        for (AsyncTaskStatus status : AsyncTaskStatus.values()) {
            assertEquals(status.name(), statuses.convertToDatabaseColumn(status));
            assertEquals(status, statuses.convertToEntityAttribute(
                    status.name()));
            assertEquals(status, AsyncTaskStatus.fromApiValue(
                    status.apiValue()));
        }
        assertNull(statuses.convertToDatabaseColumn(null));
        assertNull(statuses.convertToEntityAttribute(null));
    }

    @Test
    void readsLegacyPendingStatusAsAccepted() {
        assertEquals(
                AsyncTaskStatus.ACCEPTED,
                statuses.convertToEntityAttribute("PENDING"));
    }

    @Test
    void rejectsUnknownStatusValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> statuses.convertToEntityAttribute("UNKNOWN"));
        assertThrows(
                IllegalArgumentException.class,
                () -> AsyncTaskStatus.fromApiValue("unknown"));
    }

    @Test
    void convertsRegisteredTaskTypeInBothDirections() {
        AsyncTaskType type = type("converter_registered", "document");
        AsyncTaskTypeConverter.register(type);

        assertEquals(
                "converter_registered",
                types.convertToDatabaseColumn(type));
        assertEquals(type,
                types.convertToEntityAttribute("converter_registered"));
        assertNull(types.convertToDatabaseColumn(null));
        assertNull(types.convertToEntityAttribute(null));
    }

    @Test
    void representsUnregisteredPersistedTypeWithoutFailingRead() {
        AsyncTaskType unknown = types.convertToEntityAttribute(
                "converter_unregistered");

        assertEquals("converter_unregistered", unknown.value());
        assertEquals("unknown", unknown.resourceType());
        assertEquals(
                "UNKNOWN(converter_unregistered)",
                unknown.name());
    }

    @Test
    void rejectsConflictingRegistrationForSamePersistedValue() {
        AsyncTaskType first = type("converter_conflict", "first");
        AsyncTaskType second = type("converter_conflict", "second");
        AsyncTaskTypeConverter.register(first);

        assertThrows(
                IllegalStateException.class,
                () -> AsyncTaskTypeConverter.register(second));
    }

    @Test
    void validatesTaskTypeDefinition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AsyncTaskTypeDefinition.of("Not-Snake", "resource"));
        assertThrows(
                IllegalArgumentException.class,
                () -> AsyncTaskTypeDefinition.of("valid", " "));
        assertThrows(
                IllegalArgumentException.class,
                () -> AsyncTaskTypeDefinition.of("valid", "resource", 0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> AsyncTaskTypeDefinition.of("valid", "resource", 1, 0));
    }

    private AsyncTaskType type(String value, String resourceType) {
        return new DynamicType(
                value,
                AsyncTaskTypeDefinition.of(value, resourceType));
    }

    private record DynamicType(
            String name,
            AsyncTaskTypeDefinition definition)
            implements AsyncTaskType {
    }
}
