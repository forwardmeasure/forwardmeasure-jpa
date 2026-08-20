package com.forwardmeasure.jpa.asynctask.support;

import com.forwardmeasure.jpa.asynctask.model.AsyncTaskType;
import com.forwardmeasure.jpa.asynctask.model.AsyncTaskTypeDefinition;

public enum TestAsyncTaskType implements AsyncTaskType {
  EXTRACTION(AsyncTaskTypeDefinition.of("information_extraction", "evidence", 3600, 3)),
  SCREENING(AsyncTaskTypeDefinition.of("population_screening", "population", 7200, 2));

  private final AsyncTaskTypeDefinition definition;

  TestAsyncTaskType(AsyncTaskTypeDefinition definition) {
    this.definition = definition;
  }

  @Override
  public AsyncTaskTypeDefinition definition() {
    return definition;
  }
}
