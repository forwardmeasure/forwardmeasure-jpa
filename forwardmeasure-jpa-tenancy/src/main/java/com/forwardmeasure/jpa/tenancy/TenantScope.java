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
package com.forwardmeasure.jpa.tenancy;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Trusted tenant identity for one request or message. Physical tenantId routing is resolved only by
 * persistence infrastructure when it acquires a connection, never from request claims.
 */
public interface TenantScope {

  String UNBOUND_IDENTIFIER = "unbound_tenant";

  Optional<TenantId> current();

  Scope open(TenantId tenantId);

  default TenantId currentRequired() {
    return current()
        .orElseThrow(
            () ->
                new IllegalStateException("No tenant identity is bound to the current execution"));
  }

  default void run(TenantId tenantId, Runnable operation) {
    try (Scope ignored = open(tenantId)) {
      operation.run();
    }
  }

  default <T> T call(TenantId tenantId, Supplier<T> operation) {
    try (Scope ignored = open(tenantId)) {
      return operation.get();
    }
  }

  interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
