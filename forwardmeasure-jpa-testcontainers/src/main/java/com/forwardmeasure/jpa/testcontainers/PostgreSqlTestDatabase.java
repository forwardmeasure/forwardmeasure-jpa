package com.forwardmeasure.jpa.testcontainers;

import com.forwardmeasure.jpa.tenancy.TenantSchema;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Framework-neutral, explicitly managed PostgreSQL test fixture.
 *
 * <p>No credentials are logged and no global singleton container is retained.
 * A test suite owns this object and must close it.
 */
public final class PostgreSqlTestDatabase implements AutoCloseable {

    public static final DockerImageName DEFAULT_IMAGE =
            DockerImageName.parse("postgres:18-alpine");

    private final PostgreSQLContainer container;
    private boolean started;

    public PostgreSqlTestDatabase() {
        this(DEFAULT_IMAGE);
    }

    public PostgreSqlTestDatabase(DockerImageName image) {
        Objects.requireNonNull(image, "image");
        container = new PostgreSQLContainer(image)
                .withDatabaseName("forwardmeasure_jpa")
                .withUsername("forwardmeasure")
                .withPassword("forwardmeasure-test-only");
    }

    public synchronized PostgreSqlTestDatabase start() {
        if (!started) {
            container.start();
            started = true;
        }
        return this;
    }

    public DataSource dataSource() {
        ensureStarted();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(container.getJdbcUrl());
        dataSource.setUser(container.getUsername());
        dataSource.setPassword(container.getPassword());
        return dataSource;
    }

    public String jdbcUrl() {
        ensureStarted();
        return container.getJdbcUrl();
    }

    public String username() {
        ensureStarted();
        return container.getUsername();
    }

    public String password() {
        ensureStarted();
        return container.getPassword();
    }

    public void createSchema(TenantSchema schema) {
        Objects.requireNonNull(schema, "schema");
        // TenantSchema validation makes this identifier safe to quote.
        String sql = "create schema if not exists \"" + schema.value() + "\"";
        try (var connection = dataSource().getConnection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to create test schema " + schema.value(),
                    exception);
        }
    }

    public boolean isRunning() {
        return started && container.isRunning();
    }

    @Override
    public synchronized void close() {
        if (started) {
            container.stop();
            started = false;
        }
    }

    private void ensureStarted() {
        if (!started) {
            throw new IllegalStateException(
                    "PostgreSQL test database has not been started");
        }
    }
}
