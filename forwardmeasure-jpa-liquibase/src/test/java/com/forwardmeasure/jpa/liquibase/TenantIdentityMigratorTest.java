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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

@WithPostgreSqlContainer(databaseName = "jpa_identity_contract")
class TenantIdentityMigratorTest {

  @Test
  void createsTheIdentitySchemaOnceAndIsIdempotent(PostgreSqlTestContainer database)
      throws Exception {
    TenantIdentityMigrator migrator = new TenantIdentityMigrator(database.dataSource());

    migrator.migrate();
    var repeated = migrator.migrate();

    assertEquals(0L, repeated.appliedChangeCount());
    assertTrue(repeated.status().current());
    assertTrue(TenantSchemaMigratorTest.tableExists(database, "identity", "actor"));
    assertEquals(1L, count(database, "select count(*) from identity.databasechangelog"));
  }

  @Test
  void actorIdentityRequiresAProviderAndIsUniquePerProviderAndSubject(
      PostgreSqlTestContainer database) throws Exception {
    new TenantIdentityMigrator(database.dataSource()).migrate();
    String subject = "subject-" + UUID.randomUUID();

    try (Connection connection = database.dataSource().getConnection()) {
      insertActor(connection, "keycloak", subject, "HUMAN");
      assertThrows(SQLException.class, () -> insertActor(connection, "keycloak", subject, "HUMAN"));
      assertThrows(SQLException.class, () -> insertActor(connection, null, subject, "HUMAN"));
      assertThrows(
          SQLException.class,
          () -> insertActor(connection, "keycloak", "other-" + subject, "ROBOT"));
      // The same subject from another provider is a different actor.
      insertActor(connection, "other-idp", subject, "SERVICE");
    }
  }

  @Test
  void productSchemasReferenceTheSharedActorAcrossSchemas(PostgreSqlTestContainer database)
      throws Exception {
    new TenantIdentityMigrator(database.dataSource()).migrate();
    TenantSchema product = TenantSchema.forTenant(new TenantId(UUID.randomUUID()));
    database.createSchema(product.value());
    new TenantSchemaMigrator(
            database.dataSource(),
            "db/changelog/tenant-schema-migrator-test.xml",
            getClass().getClassLoader())
        .migrate(product);

    try (Connection connection = database.dataSource().getConnection()) {
      long owner = insertActor(connection, "keycloak", "owner-" + UUID.randomUUID(), "HUMAN");
      String insert =
          "insert into \""
              + product.value()
              + "\".owned_record (id, owner_id, name) values (?, ?, ?)";
      try (var statement = connection.prepareStatement(insert)) {
        statement.setLong(1, 1L);
        statement.setLong(2, owner);
        statement.setString(3, "owned");
        assertEquals(1, statement.executeUpdate());
      }
      assertThrows(
          SQLException.class,
          () -> {
            try (var statement = connection.prepareStatement(insert)) {
              statement.setLong(1, 2L);
              statement.setLong(2, Long.MAX_VALUE);
              statement.setString(3, "orphan");
              statement.executeUpdate();
            }
          });
    }
  }

  @Test
  void runtimeRolesOnlyReadActorsAndProvisioningNeverDeletesThem(PostgreSqlTestContainer database)
      throws Exception {
    TenantIdentityMigrator migrator = new TenantIdentityMigrator(database.dataSource());
    migrator.migrate();
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    String reader = "identity_reader_" + suffix;
    String provisioner = "identity_provisioner_" + suffix;
    createLoginRole(database, reader);
    createLoginRole(database, provisioner);

    migrator.grantRead(reader);
    migrator.grantProvisioning(provisioner);

    String subject = "provisioned-" + suffix;
    try (Connection connection = connectAs(database, provisioner)) {
      long id = insertActor(connection, "keycloak", subject, "HUMAN");
      try (var statement = connection.createStatement()) {
        assertEquals(
            1,
            statement.executeUpdate(
                "update identity.actor set disabled_at = current_timestamp where id = " + id));
        assertThrows(
            SQLException.class,
            () -> statement.executeUpdate("delete from identity.actor where id = " + id));
      }
    }
    try (Connection connection = connectAs(database, reader)) {
      try (var statement = connection.createStatement();
          var result =
              statement.executeQuery(
                  "select count(*) from identity.actor where subject_identifier = '"
                      + subject
                      + "'")) {
        result.next();
        assertEquals(1L, result.getLong(1));
      }
      assertThrows(
          SQLException.class, () -> insertActor(connection, "keycloak", "reader-made", "HUMAN"));
    }
  }

  @Test
  void rejectsUnsafeRoleNames(PostgreSqlTestContainer database) {
    TenantIdentityMigrator migrator = new TenantIdentityMigrator(database.dataSource());

    assertThrows(
        IllegalArgumentException.class, () -> migrator.grantRead("x\"; drop schema identity"));
    assertThrows(IllegalArgumentException.class, () -> migrator.grantProvisioning(""));
  }

  private static long insertActor(
      Connection connection, String provider, String subject, String type) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "insert into identity.actor (identity_provider, subject_identifier, identity_type)"
                + " values (?, ?, ?) returning id")) {
      statement.setString(1, provider);
      statement.setString(2, subject);
      statement.setString(3, type);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private static void createLoginRole(PostgreSqlTestContainer database, String role)
      throws SQLException {
    try (Connection connection = database.dataSource().getConnection();
        var statement = connection.createStatement()) {
      statement.execute("create role " + role + " login password '" + role + "'");
      statement.execute("grant connect on database \"" + database.databaseName() + "\" to " + role);
    }
  }

  private static Connection connectAs(PostgreSqlTestContainer database, String role)
      throws SQLException {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(database.hostJdbcUrl());
    dataSource.setUser(role);
    dataSource.setPassword(role);
    return dataSource.getConnection();
  }

  private static long count(PostgreSqlTestContainer database, String sql) throws SQLException {
    try (Connection connection = database.dataSource().getConnection();
        var statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }
}
