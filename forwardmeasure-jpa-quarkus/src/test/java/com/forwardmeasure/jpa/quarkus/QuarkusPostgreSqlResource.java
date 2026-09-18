package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.database.migration.api.DatabaseTarget;
import com.forwardmeasure.database.migration.api.MigrationPlan;
import com.forwardmeasure.database.migration.api.MigrationRequest;
import com.forwardmeasure.database.migration.liquibase.LiquibaseMigrationEngine;
import com.forwardmeasure.jpa.tenancy.FunctionalSchema;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlContainerConfiguration;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class QuarkusPostgreSqlResource implements QuarkusTestResourceLifecycleManager {

  // TenantScope carries TenantDatabase directly now - no TenantSchema/TenantId round-trip needed.
  static final TenantDatabase TENANT_DATABASE = TenantDatabase.forAlias("contracttest");
  static final FunctionalSchema SCHEMA = FunctionalSchema.OPENWORKFLOW;

  private PostgreSqlTestContainer database;

  @Override
  public Map<String, String> start() {
    database =
        new PostgreSqlTestContainer(
                new PostgreSqlContainerConfiguration(
                    PostgreSqlContainerConfiguration.DEFAULT_IMAGE,
                    TENANT_DATABASE.value(),
                    "forwardmeasure",
                    "forwardmeasure-test-only",
                    Optional.empty(),
                    List.of(),
                    PostgreSqlContainerConfiguration.DEFAULT_MEMORY_BYTES,
                    PostgreSqlContainerConfiguration.DEFAULT_MEMORY_SWAP_BYTES))
            .start();
    database.createSchema(SCHEMA.schemaName());
    new LiquibaseMigrationEngine(getClass().getClassLoader())
        .migrate(
            new MigrationRequest(
                database.dataSource(),
                DatabaseTarget.schema(SCHEMA.schemaName()),
                MigrationPlan.liquibase(
                    "forwardmeasure-jpa", "db/changelog/forwardmeasure-jpa-contract-tests.xml")));
    return Map.of(
        "quarkus.datasource.jdbc.url", database.hostJdbcUrl(),
        "quarkus.datasource.username", database.username(),
        "quarkus.datasource.password", database.password(),
        "forwardmeasure.jpa.functional-schema", SCHEMA.name(),
        "forwardmeasure.jpa.tenant-database.host", database.host(),
        "forwardmeasure.jpa.tenant-database.port", String.valueOf(database.mappedPort()),
        "forwardmeasure.jpa.tenant-database.username", database.username(),
        "forwardmeasure.jpa.tenant-database.password", database.password());
  }

  @Override
  public void stop() {
    if (database != null) {
      database.close();
      database = null;
    }
  }
}
