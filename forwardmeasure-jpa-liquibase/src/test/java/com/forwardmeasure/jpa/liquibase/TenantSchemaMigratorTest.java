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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGPoolingDataSource;

@WithPostgreSqlContainer(databaseName = "jpa_liquibase_contract")
class TenantSchemaMigratorTest {

  private static final String CHANGELOG = "db/changelog/tenant-schema-migrator-test.xml";

  @BeforeAll
  static void migrateIdentity(PostgreSqlTestContainer database) {
    // Product schemas reference the tenant's shared identity.actor, so it exists first.
    new TenantIdentityMigrator(database.dataSource()).migrate();
  }

  @Test
  void migrationExceptionIdentifiesTheOperationAndTenantSchema() {
    TenantSchema tenant = TenantSchema.forTenant(new TenantId(UUID.randomUUID()));
    IllegalStateException cause = new IllegalStateException("database");

    TenantMigrationException migrationFailure = new TenantMigrationException(tenant, cause);
    assertEquals(
        "Failed to migrate tenant schema " + tenant.value(), migrationFailure.getMessage());
    assertEquals(cause, migrationFailure.getCause());

    TenantMigrationException validationFailure =
        new TenantMigrationException(tenant, "validate", cause);
    assertEquals(
        "Failed to validate tenant schema " + tenant.value(), validationFailure.getMessage());
    assertEquals(cause, validationFailure.getCause());
  }

  @Test
  void migratesEachTenantIndependentlyAndIsIdempotent(PostgreSqlTestContainer database)
      throws Exception {
    TenantSchema first = TenantSchema.forTenant(new TenantId(UUID.randomUUID()));
    TenantSchema second = TenantSchema.forTenant(new TenantId(UUID.randomUUID()));
    database.createSchema(first.value());
    database.createSchema(second.value());
    TenantSchemaMigrator migrator = migrator(database);

    assertTrue(migrator.validate(first).valid());
    assertEquals(1L, migrator.status(first).pendingCount());

    var firstMigration = migrator.migrate(first);
    var repeatedMigration = migrator.migrate(first);

    assertEquals(1L, firstMigration.appliedChangeCount());
    assertEquals(0L, repeatedMigration.appliedChangeCount());
    assertTrue(repeatedMigration.status().current());
    assertTrue(tableExists(database, first.value(), "owned_record"));
    assertFalse(tableExists(database, second.value(), "owned_record"));
    assertEquals(1L, changeSetCount(database, first));

    migrator.migrate(second);

    assertTrue(tableExists(database, second.value(), "owned_record"));
    assertEquals(1L, changeSetCount(database, second));
    // No product schema carries its own actor table: there is one, in the identity schema.
    assertFalse(tableExists(database, first.value(), "actor"));
    assertFalse(tableExists(database, second.value(), "actor"));
    assertTrue(tableExists(database, "identity", "actor"));
  }

  @Test
  @SuppressWarnings("deprecation")
  void resetsPooledConnectionToPublicAfterMigration(PostgreSqlTestContainer database)
      throws Exception {
    TenantSchema tenant = TenantSchema.forTenant(new TenantId(UUID.randomUUID()));
    database.createSchema(tenant.value());

    PGPoolingDataSource pool = new PGPoolingDataSource();
    pool.setDataSourceName("forwardmeasure-jpa-" + UUID.randomUUID());
    pool.setUrl(database.hostJdbcUrl());
    pool.setUser(database.username());
    pool.setPassword(database.password());
    pool.setInitialConnections(1);
    pool.setMaxConnections(1);
    try {
      new TenantSchemaMigrator(pool, CHANGELOG, getClass().getClassLoader()).migrate(tenant);
      try (var connection = pool.getConnection()) {
        assertEquals(TenantSchema.PUBLIC.value(), connection.getSchema());
      }
    } finally {
      pool.close();
    }
  }

  private TenantSchemaMigrator migrator(PostgreSqlTestContainer database) {
    return new TenantSchemaMigrator(database.dataSource(), CHANGELOG, getClass().getClassLoader());
  }

  static boolean tableExists(PostgreSqlTestContainer database, String schema, String table)
      throws Exception {
    try (var connection = database.dataSource().getConnection();
        var statement =
            connection.prepareStatement(
                "select count(*) from information_schema.tables"
                    + " where table_schema = ? and table_name = ?")) {
      statement.setString(1, schema);
      statement.setString(2, table);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getLong(1) == 1L;
      }
    }
  }

  private long changeSetCount(PostgreSqlTestContainer database, TenantSchema schema)
      throws Exception {
    try (var connection = database.dataSource().getConnection()) {
      connection.setSchema(schema.value());
      try (var statement = connection.createStatement();
          var result = statement.executeQuery("select count(*) from databasechangelog")) {
        result.next();
        return result.getLong(1);
      }
    }
  }
}
