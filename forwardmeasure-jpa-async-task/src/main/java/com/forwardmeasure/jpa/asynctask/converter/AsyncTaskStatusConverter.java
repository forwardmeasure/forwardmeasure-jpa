package com.forwardmeasure.jpa.asynctask.converter;

import com.forwardmeasure.jpa.asynctask.model.AsyncTaskStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AsyncTaskStatusConverter
        implements AttributeConverter<AsyncTaskStatus, String> {

    @Override
    public String convertToDatabaseColumn(AsyncTaskStatus value) {
        return value == null ? null : value.databaseValue();
    }

    @Override
    public AsyncTaskStatus convertToEntityAttribute(String value) {
        return value == null
                ? null
                : AsyncTaskStatus.fromDatabaseValue(value);
    }
}
