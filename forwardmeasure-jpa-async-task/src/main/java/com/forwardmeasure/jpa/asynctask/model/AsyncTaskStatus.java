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
package com.forwardmeasure.jpa.asynctask.model;

import java.util.Locale;

/** Durable lifecycle state for an asynchronous task. */
public enum AsyncTaskStatus {
  ACCEPTED,
  PROCESSING,
  COMPLETED,
  FAILED,
  CANCELLED,
  SKIPPED;

  public String databaseValue() {
    return name();
  }

  public String apiValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == CANCELLED || this == SKIPPED;
  }

  public static AsyncTaskStatus fromDatabaseValue(String value) {
    if ("PENDING".equals(value)) {
      return ACCEPTED;
    }
    return valueOf(value);
  }

  public static AsyncTaskStatus fromApiValue(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
