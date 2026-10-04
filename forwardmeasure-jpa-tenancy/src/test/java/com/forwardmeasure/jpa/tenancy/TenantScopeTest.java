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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class TenantScopeTest {

  @Test
  void nestedScopeRestoresAndThenClearsTenant() {
    ThreadBoundTenantScope scope = new ThreadBoundTenantScope();
    TenantDatabase first = TenantDatabase.forAlias("lux");
    TenantDatabase second = TenantDatabase.forAlias("acme");

    try (TenantScope.Scope ignored = scope.open(first)) {
      assertEquals(first, scope.currentRequired());
      try (TenantScope.Scope nested = scope.open(second)) {
        assertEquals(second, scope.currentRequired());
      }
      assertEquals(first, scope.currentRequired());
    }

    assertTrue(scope.current().isEmpty());
  }

  @Test
  void scopesMustCloseInReverseOrder() {
    ThreadBoundTenantScope scope = new ThreadBoundTenantScope();
    TenantScope.Scope outer = scope.open(TenantDatabase.forAlias("lux"));
    TenantScope.Scope inner = scope.open(TenantDatabase.forAlias("acme"));

    assertThrows(IllegalStateException.class, outer::close);
    inner.close();
    outer.close();
    assertTrue(scope.current().isEmpty());
  }

  @Test
  void scopeCannotBeClosedFromAnotherThread() throws Exception {
    ThreadBoundTenantScope scope = new ThreadBoundTenantScope();
    TenantScope.Scope tenant = scope.open(TenantDatabase.forAlias("lux"));

    try (var executor = Executors.newSingleThreadExecutor()) {
      ExecutionException failure =
          assertThrows(ExecutionException.class, () -> executor.submit(tenant::close).get());
      assertTrue(failure.getCause() instanceof IllegalStateException);
    }

    tenant.close();
    assertTrue(scope.current().isEmpty());
  }
}
