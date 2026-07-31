package com.forwardmeasure.jpa.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.jpa.testcontainers.PostgreSqlTestDatabase;
import com.forwardmeasure.jpa.testcontainers.PostgreSqlTestDatabaseExtension;
import java.util.UUID;
import org.postgresql.ds.PGPoolingDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(PostgreSqlTestDatabaseExtension.class)
class TenantSchemaMigratorTest {

    @Test
    void migratesEachTenantIndependentlyAndIsIdempotent(
            PostgreSqlTestDatabase database) throws Exception {
        TenantSchema first = TenantSchema.forTenant(
                new TenantId(UUID.randomUUID()));
        TenantSchema second = TenantSchema.forTenant(
                new TenantId(UUID.randomUUID()));
        database.createSchema(first);
        database.createSchema(second);
        TenantSchemaMigrator migrator =
                new TenantSchemaMigrator(database.dataSource());

        migrator.migrate(first);
        migrator.migrate(first);

        assertTrue(tableExists(database, first, "actor"));
        assertFalse(tableExists(database, second, "actor"));
        assertEquals(5L, changeSetCount(database, first));

        migrator.migrate(second);

        assertTrue(tableExists(database, second, "actor"));
        assertEquals(5L, changeSetCount(database, second));
        assertNullProviderIdentityIsUnique(database, second);
    }

    @Test
    @SuppressWarnings("deprecation")
    void resetsPooledConnectionToPublicAfterMigration(
            PostgreSqlTestDatabase database) throws Exception {
        TenantSchema tenant = TenantSchema.forTenant(
                new TenantId(UUID.randomUUID()));
        database.createSchema(tenant);

        PGPoolingDataSource pool = new PGPoolingDataSource();
        pool.setDataSourceName(
                "forwardmeasure-jpa-" + UUID.randomUUID());
        pool.setUrl(database.jdbcUrl());
        pool.setUser(database.username());
        pool.setPassword(database.password());
        pool.setInitialConnections(1);
        pool.setMaxConnections(1);
        try {
            new TenantSchemaMigrator(pool).migrate(tenant);
            try (var connection = pool.getConnection()) {
                assertEquals(
                        TenantSchema.PUBLIC.value(),
                        connection.getSchema());
            }
        } finally {
            pool.close();
        }
    }

    private boolean tableExists(
            PostgreSqlTestDatabase database,
            TenantSchema schema,
            String table) throws Exception {
        try (var connection = database.dataSource().getConnection();
                var statement = connection.prepareStatement(
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

    private long changeSetCount(
            PostgreSqlTestDatabase database,
            TenantSchema schema) throws Exception {
        try (var connection = database.dataSource().getConnection()) {
            connection.setSchema(schema.value());
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery(
                            "select count(*) from databasechangelog")) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private void assertNullProviderIdentityIsUnique(
            PostgreSqlTestDatabase database,
            TenantSchema schema) throws Exception {
        String insert = """
                insert into actor (
                    id, version, uuid, subject_identifier, identity_type,
                    identity_provider
                ) values (
                    nextval('actor_id_seq'), 0, ?, 'local-subject', 'HUMAN',
                    null
                )
                """;
        try (var connection = database.dataSource().getConnection()) {
            connection.setSchema(schema.value());
            try (var statement = connection.prepareStatement(insert)) {
                statement.setObject(1, UUID.randomUUID());
                statement.executeUpdate();
            }
            assertThrows(
                    java.sql.SQLException.class,
                    () -> {
                        try (var statement =
                                connection.prepareStatement(insert)) {
                            statement.setObject(1, UUID.randomUUID());
                            statement.executeUpdate();
                        }
                    });
        }
    }
}
