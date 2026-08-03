package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import java.util.UUID;

public final class QuarkusPostgreSqlResource
        implements QuarkusTestResourceLifecycleManager {

    static final TenantSchema TENANT = TenantSchema.forTenant(
            new TenantId(UUID.fromString(
                    "10000000-0000-0000-0000-000000000001")));

    private PostgreSqlTestContainer database;

    @Override
    public Map<String, String> start() {
        database = new PostgreSqlTestContainer().start();
        database.createSchema(TENANT.value());
        new TenantSchemaMigrator(
                database.dataSource(),
                "db/changelog/forwardmeasure-jpa-contract-tests.xml",
                getClass().getClassLoader())
                .migrate(TENANT);
        return Map.of(
                "quarkus.datasource.jdbc.url", database.hostJdbcUrl(),
                "quarkus.datasource.username", database.username(),
                "quarkus.datasource.password", database.password());
    }

    @Override
    public void stop() {
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
