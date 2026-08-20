package com.forwardmeasure.jpa.asynctask.converter;

import com.forwardmeasure.jpa.asynctask.model.AsyncTaskType;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskTypeDefinition;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Converter
public class AsyncTaskTypeConverter implements AttributeConverter<AsyncTaskType, String> {

  private static final Map<String, AsyncTaskType> TYPES = new ConcurrentHashMap<>();

  public static void register(AsyncTaskType... types) {
    Objects.requireNonNull(types, "types");
    for (AsyncTaskType type : types) {
      AsyncTaskType required = Objects.requireNonNull(type, "type");
      AsyncTaskType previous = TYPES.putIfAbsent(required.value(), required);
      if (previous != null && !previous.equals(required)) {
        throw new IllegalStateException(
            "Async task type is already registered: " + required.value());
      }
    }
  }

  @Override
  public String convertToDatabaseColumn(AsyncTaskType value) {
    return value == null ? null : value.value();
  }

  @Override
  public AsyncTaskType convertToEntityAttribute(String value) {
    if (value == null) {
      return null;
    }
    return TYPES.getOrDefault(value, new UnknownAsyncTaskType(value));
  }

  private record UnknownAsyncTaskType(String value) implements AsyncTaskType {

    @Override
    public AsyncTaskTypeDefinition definition() {
      return AsyncTaskTypeDefinition.of(value, "unknown");
    }

    @Override
    public String name() {
      return "UNKNOWN(" + value + ")";
    }
  }
}
