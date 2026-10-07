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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.datasource.TenantDataSourceRegistry;
import com.forwardmeasure.jpa.datasource.TenantDataSourceTemplate;
import com.forwardmeasure.jpa.tenancy.Did;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@WithPostgreSqlContainer(databaseName = "registry_datasource_contract")
class RegistryTenantDataSourceTest {
  @Test
  void cachedRoutingBoundaryRechecksActivationOnEveryBorrow(PostgreSqlTestContainer database)
      throws Exception {
    var registry = new TenantRegistry(database.dataSource());
    registry.migrate();
    String alias = "route" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    var did = Did.parse("did:web:" + alias + ".example.test");
    var tenant = TenantId.forDid(did);
    var target = TenantDatabase.forAlias(alias);
    registry.register(did, alias, target);
    var template =
        new TenantDataSourceTemplate(
            "jdbc:postgresql://" + database.host() + ":" + database.mappedPort() + "/",
            database.username(),
            database.password());
    try (var pools =
        new TenantDataSourceRegistry(
            template,
            (resolved, configuration) -> {
              assertEquals(target, resolved);
              return database.dataSource();
            })) {
      var routed = new RegistryTenantDataSource(registry, pools, tenant);
      try (var connection = routed.getConnection()) {
        assertTrue(connection.isValid(2));
      }
      try (var connection = database.dataSource().getConnection();
          var update =
              connection.prepareStatement(
                  "UPDATE tenant_registry SET status = 'DEPROVISIONING' WHERE tenant_id = ?")) {
        update.setObject(1, tenant.value());
        assertEquals(1, update.executeUpdate());
      }
      assertThrows(IllegalStateException.class, routed::getConnection);
      assertThrows(
          IllegalStateException.class,
          () -> routed.getConnection(database.username(), database.password()));
      registry.register(did, alias, target);
      try (var connection = routed.getConnection(database.username(), database.password())) {
        assertTrue(connection.isValid(2));
      }
      assertTrue(routed.isWrapperFor(DataSource.class));
      assertSame(routed, routed.unwrap(DataSource.class));
      assertFalse(routed.isWrapperFor(org.postgresql.ds.PGSimpleDataSource.class));
      assertThrows(
          SQLException.class, () -> routed.unwrap(org.postgresql.ds.PGSimpleDataSource.class));
    }
  }
}
