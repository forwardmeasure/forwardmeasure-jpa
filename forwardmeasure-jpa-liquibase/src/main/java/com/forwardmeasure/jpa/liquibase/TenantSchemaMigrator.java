package com.forwardmeasure.jpa.liquibase;

import com.forwardmeasure.database.migration.api.DatabaseTarget;
import com.forwardmeasure.database.migration.api.MigrationException;
import com.forwardmeasure.database.migration.api.MigrationPlan;
import com.forwardmeasure.database.migration.api.MigrationRequest;
import com.forwardmeasure.database.migration.api.MigrationResult;
import com.forwardmeasure.database.migration.api.MigrationStatus;
import com.forwardmeasure.database.migration.api.MigrationValidation;
import com.forwardmeasure.database.migration.liquibase.LiquibaseMigrationEngine;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Applies the ForwardMeasure JPA schema to one validated tenant schema.
 *
 * <p>The caller owns scheduling and decides which tenants are migrated. Each
 * invocation acquires and closes its own JDBC connection. Liquibase's lock and
 * history tables are consequently isolated in the target tenant schema.
 */
public final class TenantSchemaMigrator {

    public static final String PLAN_ID = "forwardmeasure-jpa";

    public static final String DEFAULT_CHANGELOG =
            "db/changelog/forwardmeasure-jpa.xml";

    private final DataSource dataSource;
    private final MigrationPlan plan;
    private final LiquibaseMigrationEngine engine;

    public TenantSchemaMigrator(DataSource dataSource) {
        this(dataSource, DEFAULT_CHANGELOG,
                Thread.currentThread().getContextClassLoader());
    }

    public TenantSchemaMigrator(
            DataSource dataSource,
            String changelog,
            ClassLoader classLoader) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.plan = MigrationPlan.liquibase(
                PLAN_ID, Objects.requireNonNull(changelog, "changelog"));
        this.engine = new LiquibaseMigrationEngine(
                Objects.requireNonNull(classLoader, "classLoader"));
    }

    public MigrationValidation validate(TenantSchema schema) {
        return execute(schema, "validate", engine::validate);
    }

    public MigrationStatus status(TenantSchema schema) {
        return execute(schema, "inspect", engine::status);
    }

    public MigrationResult migrate(TenantSchema schema) {
        return execute(schema, "migrate", engine::migrate);
    }

    private <T> T execute(
            TenantSchema schema,
            String operation,
            MigrationCall<T> call) {
        Objects.requireNonNull(schema, "schema");
        MigrationRequest request = new MigrationRequest(
                dataSource,
                DatabaseTarget.schema(schema.value()),
                plan);
        try {
            return call.execute(request);
        } catch (MigrationException exception) {
            throw new TenantMigrationException(schema, operation, exception);
        }
    }

    @FunctionalInterface
    private interface MigrationCall<T> {
        T execute(MigrationRequest request);
    }
}
