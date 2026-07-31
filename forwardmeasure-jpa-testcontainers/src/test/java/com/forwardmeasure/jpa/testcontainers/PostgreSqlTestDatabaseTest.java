package com.forwardmeasure.jpa.testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(PostgreSqlTestDatabaseExtension.class)
class PostgreSqlTestDatabaseTest {

    @Test
    void startsRealPostgresAndCreatesValidatedTenantSchema(
            PostgreSqlTestDatabase database) throws Exception {
        TenantSchema schema = TenantSchema.forTenant(
                new TenantId(UUID.randomUUID()));

        database.createSchema(schema);

        try (var connection = database.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select schema_name from information_schema.schemata"
                                + " where schema_name = ?")) {
            statement.setString(1, schema.value());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(schema.value(), result.getString(1));
            }
        }
    }
}
