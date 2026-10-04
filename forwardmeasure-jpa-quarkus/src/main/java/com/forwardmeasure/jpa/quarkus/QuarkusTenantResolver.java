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

import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Connects the explicit ForwardMeasure tenant scope to Quarkus Hibernate ORM. An unscoped operation
 * fails closed instead of silently using a real tenant's own database.
 */
@PersistenceUnitExtension
@ApplicationScoped
public class QuarkusTenantResolver implements TenantResolver {

  private final TenantScope tenantScope;

  @Inject
  public QuarkusTenantResolver(TenantScope tenantScope) {
    this.tenantScope = tenantScope;
  }

  @Override
  public String getDefaultTenantId() {
    return TenantDatabase.UNBOUND_IDENTIFIER;
  }

  @Override
  public String resolveTenantId() {
    return tenantScope.currentRequired().value();
  }
}
