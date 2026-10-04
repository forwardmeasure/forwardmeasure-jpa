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
package com.forwardmeasure.jpa.testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Proves {@link WithMigratedTenantSchema} for real: the framework base applies, every listed extra
 * changelog applies in order, and both the container and the migrated schema are shared for the
 * whole test class - migrated exactly once, not once per {@code @Test} method.
 */
@WithMigratedTenantSchema(changelogs = "db/changelog/forwardmeasure-jpa-locking.xml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WithMigratedTenantSchemaTest {

  private static String firstSchemaValue;
  private static PostgreSqlTestContainer firstContainer;

  @Test
  @Order(1)
  void appliesTheFrameworkBaseAndEveryListedChangelogInOrder(
      TenantSchema schema, PostgreSqlTestContainer database) throws Exception {
    assertTrue(tableExists(database, schema, "actor"), "expected framework base table 'actor'");
    assertTrue(
        tableExists(database, schema, "system_lock"),
        "expected the extra changelog's table 'system_lock'");

    firstSchemaValue = schema.value();
    firstContainer = database;
  }

  @Test
  @Order(2)
  void eachTestMethodReusesTheSameMigratedSchemaOnTheSameSharedContainer(
      TenantSchema schema, PostgreSqlTestContainer database) throws Exception {
    // The schema is migrated once in beforeAll, not once per method - re-running the full
    // Liquibase chain (locking, checksum validation, one DB round trip per changelog) for every
    // @Test would be real, avoidable overhead a class-level schema doesn't need to pay.
    assertEquals(
        firstSchemaValue, schema.value(), "expected the same schema reused across test methods");
    assertSame(firstContainer, database, "expected one container reused for the whole test class");

    assertTrue(tableExists(database, schema, "actor"));
    assertTrue(tableExists(database, schema, "system_lock"));
  }

  private boolean tableExists(PostgreSqlTestContainer database, TenantSchema schema, String table)
      throws Exception {
    try (var connection = database.dataSource().getConnection();
        var statement =
            connection.prepareStatement(
                "select count(*) from information_schema.tables"
                    + " where table_schema = ? and table_name = ?")) {
      statement.setString(1, schema.value());
      statement.setString(2, table);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getLong(1) == 1L;
      }
    }
  }
}
