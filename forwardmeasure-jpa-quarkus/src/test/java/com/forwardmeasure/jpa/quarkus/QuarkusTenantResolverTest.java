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
package com.forwardmeasure.jpa.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.jpa.tenancy.ThreadBoundTenantScope;
import org.junit.jupiter.api.Test;

class QuarkusTenantResolverTest {

  @Test
  void defaultsToPublicAndFailsClosedWithoutAnExplicitScope() {
    QuarkusTenantResolver resolver = new QuarkusTenantResolver(new ThreadBoundTenantScope());

    assertEquals(TenantScope.UNBOUND_IDENTIFIER, resolver.getDefaultTenantId());
    assertThrows(IllegalStateException.class, resolver::resolveTenantId);
  }

  @Test
  void resolvesTheExplicitTenantScope() {
    TenantScope scope = new ThreadBoundTenantScope();
    TenantId tenant =
        TenantId.forDid(
            com.forwardmeasure.jpa.tenancy.Did.parse("did:fwmtest:tenant:resolvertest"));
    QuarkusTenantResolver resolver = new QuarkusTenantResolver(scope);

    try (TenantScope.Scope ignored = scope.open(tenant)) {
      assertEquals(tenant.toString(), resolver.resolveTenantId());
    }
  }
}
