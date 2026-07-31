package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.tenancy.TenantSchema;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.Unremovable;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantConnectionResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

@PersistenceUnitExtension
@ApplicationScoped
@Unremovable
public class QuarkusTenantConnectionResolver
        implements TenantConnectionResolver {

    @Inject
    Instance<AgroalDataSource> dataSource;

    @Override
    public ConnectionProvider resolve(String tenantId) {
        TenantSchema schema = new TenantSchema(tenantId);
        if (!dataSource.isResolvable()) {
            throw new IllegalStateException(
                    "No active Agroal datasource is available");
        }
        AgroalDataSource resolved = dataSource.get();
        return new SchemaConnectionProvider(resolved, schema);
    }

    private static final class SchemaConnectionProvider
            implements ConnectionProvider {

        private static final long serialVersionUID = 1L;

        private final AgroalDataSource dataSource;
        private final TenantSchema schema;

        private SchemaConnectionProvider(
                AgroalDataSource dataSource, TenantSchema schema) {
            this.dataSource = dataSource;
            this.schema = schema;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = dataSource.getConnection();
            try {
                connection.setSchema(schema.value());
                return connection;
            } catch (SQLException exception) {
                connection.close();
                throw exception;
            }
        }

        @Override
        public void closeConnection(Connection connection)
                throws SQLException {
            if (connection == null || connection.isClosed()) {
                return;
            }
            try {
                connection.setSchema(TenantSchema.PUBLIC.value());
            } finally {
                connection.close();
            }
        }

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(Class<?> unwrapType) {
            return unwrapType.isInstance(this);
        }

        @Override
        public <T> T unwrap(Class<T> unwrapType) {
            if (isUnwrappableAs(unwrapType)) {
                return unwrapType.cast(this);
            }
            throw new IllegalArgumentException(
                    "Unsupported unwrap type " + unwrapType.getName());
        }
    }
}
