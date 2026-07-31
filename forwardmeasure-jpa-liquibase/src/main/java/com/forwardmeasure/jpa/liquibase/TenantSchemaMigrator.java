package com.forwardmeasure.jpa.liquibase;

import com.forwardmeasure.jpa.tenancy.TenantSchema;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Applies the ForwardMeasure JPA schema to one validated tenant schema.
 *
 * <p>The caller owns scheduling and decides which tenants are migrated. Each
 * invocation acquires and closes its own JDBC connection. Liquibase's lock and
 * history tables are consequently isolated in the target tenant schema.
 */
public final class TenantSchemaMigrator {

    public static final String DEFAULT_CHANGELOG =
            "db/changelog/forwardmeasure-jpa.xml";

    private final DataSource dataSource;
    private final String changelog;
    private final ClassLoader classLoader;

    public TenantSchemaMigrator(DataSource dataSource) {
        this(dataSource, DEFAULT_CHANGELOG,
                Thread.currentThread().getContextClassLoader());
    }

    public TenantSchemaMigrator(
            DataSource dataSource,
            String changelog,
            ClassLoader classLoader) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.changelog = Objects.requireNonNull(changelog, "changelog");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    public void migrate(TenantSchema schema) {
        Objects.requireNonNull(schema, "schema");
        try (Connection connection = dataSource.getConnection()) {
            Liquibase liquibase = null;
            Throwable migrationFailure = null;
            try {
                connection.setSchema(schema.value());
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(
                                new JdbcConnection(connection));
                database.setDefaultSchemaName(schema.value());
                database.setLiquibaseSchemaName(schema.value());
                liquibase = new Liquibase(
                        changelog,
                        new ClassLoaderResourceAccessor(classLoader),
                        database);
                liquibase.update(new Contexts(), new LabelExpression());
            } catch (SQLException | LiquibaseException
                    | RuntimeException | Error failure) {
                migrationFailure = failure;
                throw failure;
            } finally {
                // Liquibase.close() closes its logical JDBC connection.
                // Reset first so a pooled physical connection cannot retain
                // the previous tenant's schema.
                cleanup(connection, liquibase, migrationFailure);
            }
        } catch (SQLException | LiquibaseException exception) {
            throw new TenantMigrationException(schema, exception);
        }
    }

    private void cleanup(
            Connection connection,
            Liquibase liquibase,
            Throwable migrationFailure)
            throws SQLException, LiquibaseException {
        Exception cleanupFailure = null;
        try {
            if (!connection.isClosed()) {
                connection.setSchema(TenantSchema.PUBLIC.value());
            }
        } catch (SQLException resetFailure) {
            cleanupFailure = resetFailure;
        }

        try {
            if (liquibase != null) {
                liquibase.close();
            }
        } catch (LiquibaseException closeFailure) {
            if (cleanupFailure == null) {
                cleanupFailure = closeFailure;
            } else {
                cleanupFailure.addSuppressed(closeFailure);
            }
        }

        if (cleanupFailure == null) {
            return;
        }
        if (migrationFailure != null) {
            migrationFailure.addSuppressed(cleanupFailure);
            return;
        }
        if (cleanupFailure instanceof SQLException sqlFailure) {
            throw sqlFailure;
        }
        throw (LiquibaseException) cleanupFailure;
    }
}
