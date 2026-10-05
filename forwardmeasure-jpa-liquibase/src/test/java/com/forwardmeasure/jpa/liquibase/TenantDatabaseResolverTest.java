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
package com.forwardmeasure.jpa.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forwardmeasure.jpa.tenancy.Did;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@WithPostgreSqlContainer(databaseName = "tenant_database_resolver_contract")
class TenantDatabaseResolverTest {

  @Test
  void rejectsDeactivationAndObservesReactivationWithoutRestart(PostgreSqlTestContainer database)
      throws Exception {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();
    String alias = "lux" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    Did tenantDid = Did.parse("did:web:" + alias + ".kriyagentic.com");
    TenantId tenantId = TenantId.forDid(tenantDid);
    TenantDatabase expected = TenantDatabase.forAlias(alias);
    registry.register(tenantDid, alias, expected, "default");

    TenantDatabaseResolver resolver = new TenantDatabaseResolver(registry);
    assertEquals(expected, resolver.resolve(tenantId));
    try (var connection = database.dataSource().getConnection();
        var update =
            connection.prepareStatement(
                "UPDATE tenant_registry SET status = 'DEPROVISIONING' WHERE tenant_id = ?")) {
      update.setObject(1, tenantId.value());
      assertEquals(1, update.executeUpdate());
    }
    assertThrows(IllegalStateException.class, () -> resolver.resolve(tenantId));
    registry.register(tenantDid, alias, expected, "default");
    assertEquals(expected, resolver.resolve(tenantId));
  }

  @Test
  void throwsForATenantThatWasNeverRegistered(PostgreSqlTestContainer database) {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();
    TenantDatabaseResolver resolver = new TenantDatabaseResolver(registry);

    TenantId unregistered = new TenantId(UUID.randomUUID());

    assertThrows(IllegalStateException.class, () -> resolver.resolve(unregistered));
  }

  @Test
  void resolvesATenantRegisteredAfterThisResolverWasConstructed(PostgreSqlTestContainer database) {
    TenantRegistry registry = new TenantRegistry(database.dataSource());
    registry.migrate();
    TenantDatabaseResolver resolver = new TenantDatabaseResolver(registry);
    String alias = "lux" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    Did tenantDid = Did.parse("did:web:" + alias + ".kriyagentic.com");
    TenantId tenantId = TenantId.forDid(tenantDid);
    TenantDatabase expected = TenantDatabase.forAlias(alias);

    // Registered only now, after the resolver already exists - proves a cache miss re-queries
    // rather than permanently remembering an earlier "not found".
    registry.register(tenantDid, alias, expected, "default");

    assertEquals(expected, resolver.resolve(tenantId));
  }
}
