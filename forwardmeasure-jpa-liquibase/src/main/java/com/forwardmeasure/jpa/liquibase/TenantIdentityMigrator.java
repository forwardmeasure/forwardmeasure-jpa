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

import com.forwardmeasure.database.migration.api.DatabaseTarget;
import com.forwardmeasure.database.migration.api.MigrationException;
import com.forwardmeasure.database.migration.api.MigrationPlan;
import com.forwardmeasure.database.migration.api.MigrationRequest;
import com.forwardmeasure.database.migration.api.MigrationResult;
import com.forwardmeasure.database.migration.liquibase.LiquibaseMigrationEngine;
import com.forwardmeasure.jpa.tenancy.IdentitySchema;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * Creates and migrates a tenant database's {@link IdentitySchema} - the one actor table every
 * product in that tenant references - and grants roles access to it.
 *
 * <p>Every product's tenant provisioning calls {@link #migrate()} before migrating its own
 * functional schema, so whichever product provisions a tenant database first creates the identity
 * schema and the rest find it already applied (Liquibase records it in the identity schema's own
 * changelog table; concurrent provisioning is serialised by a transaction-scoped advisory lock and
 * by Liquibase's own lock). It must run on an administrative connection to the tenant database.
 *
 * <p>Access is deliberately asymmetric: products' runtime roles only read actors ({@link
 * #grantRead}); only the tenant-access provisioning identity writes them ({@link
 * #grantProvisioning}), because actors are provisioned when tenant access is granted and are never
 * created on first sight.
 */
public final class TenantIdentityMigrator {

  public static final String PLAN_ID = "forwardmeasure-jpa-identity";
  public static final String CHANGELOG = "db/changelog/forwardmeasure-jpa-identity.xml";

  private static final Pattern ROLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_$-]{0,62}");
  private static final String SCHEMA = "\"" + IdentitySchema.NAME + "\"";

  private final DataSource tenantDataSource;
  private final LiquibaseMigrationEngine engine;

  public TenantIdentityMigrator(DataSource tenantDataSource) {
    this(tenantDataSource, TenantIdentityMigrator.class.getClassLoader());
  }

  public TenantIdentityMigrator(DataSource tenantDataSource, ClassLoader classLoader) {
    this.tenantDataSource = Objects.requireNonNull(tenantDataSource, "tenantDataSource");
    this.engine = new LiquibaseMigrationEngine(Objects.requireNonNull(classLoader, "classLoader"));
  }

  /** Creates the identity schema if absent and applies the identity changelog into it. */
  public MigrationResult migrate() {
    execute(
        "create",
        "SELECT pg_advisory_xact_lock(hashtext('forwardmeasure.identity-schema'));"
            + " CREATE SCHEMA IF NOT EXISTS "
            + SCHEMA
            + ";");
    MigrationRequest request =
        new MigrationRequest(
            tenantDataSource,
            DatabaseTarget.schema(IdentitySchema.NAME),
            MigrationPlan.liquibase(PLAN_ID, CHANGELOG));
    try {
      return engine.migrate(request);
    } catch (MigrationException exception) {
      throw new TenantIdentityMigrationException("migrate", exception);
    }
  }

  /** Lets a product's runtime role read actors - and nothing else in the identity schema. */
  public void grantRead(String role) {
    String grantee = quote(role);
    execute(
        "grant read on",
        "GRANT USAGE ON SCHEMA "
            + SCHEMA
            + " TO "
            + grantee
            + "; GRANT SELECT ON TABLE "
            + SCHEMA
            + ".actor TO "
            + grantee
            + ";");
  }

  /**
   * Lets the tenant-access provisioning identity create and disable actors. It may not delete them:
   * an actor whose access is removed is disabled, so every record it owns keeps its owner.
   */
  public void grantProvisioning(String role) {
    String grantee = quote(role);
    execute(
        "grant provisioning on",
        "GRANT USAGE ON SCHEMA "
            + SCHEMA
            + " TO "
            + grantee
            + "; GRANT SELECT, INSERT, UPDATE ON TABLE "
            + SCHEMA
            + ".actor TO "
            + grantee
            + "; GRANT USAGE, SELECT ON SEQUENCE "
            + SCHEMA
            + ".actor_id_seq TO "
            + grantee
            + ";");
  }

  private void execute(String operation, String sql) {
    try (Connection connection = tenantDataSource.getConnection()) {
      boolean autoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(sql);
        connection.commit();
      } catch (SQLException failure) {
        connection.rollback();
        throw failure;
      } finally {
        connection.setAutoCommit(autoCommit);
      }
    } catch (SQLException exception) {
      throw new TenantIdentityMigrationException(operation, exception);
    }
  }

  private static String quote(String role) {
    Objects.requireNonNull(role, "role");
    if (!ROLE.matcher(role).matches()) {
      throw new IllegalArgumentException("Not a safe role name: " + role);
    }
    return "\"" + role + "\"";
  }
}
