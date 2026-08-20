package com.forwardmeasure.jpa.micronaut;

import com.forwardmeasure.jpa.tenancy.TenantSchema;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

public final class MicronautSchemaConnectionProvider
    implements MultiTenantConnectionProvider<String> {

  private static final long serialVersionUID = 1L;

  private final DataSource dataSource;

  public MicronautSchemaConnectionProvider(DataSource dataSource) {
    this.dataSource =
        DelegatingDataSource.unwrapDataSource(Objects.requireNonNull(dataSource, "dataSource"));
  }

  @Override
  public Connection getAnyConnection() throws SQLException {
    return dataSource.getConnection();
  }

  @Override
  public void releaseAnyConnection(Connection connection) throws SQLException {
    connection.close();
  }

  @Override
  public Connection getConnection(String tenantIdentifier) throws SQLException {
    TenantSchema schema = new TenantSchema(tenantIdentifier);
    Connection connection = getAnyConnection();
    try {
      connection.setSchema(schema.value());
      return connection;
    } catch (SQLException exception) {
      connection.close();
      throw exception;
    }
  }

  @Override
  public void releaseConnection(String tenantIdentifier, Connection connection)
      throws SQLException {
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
    throw new IllegalArgumentException("Unsupported unwrap type " + unwrapType.getName());
  }

  @Override
  public boolean handlesConnectionSchema() {
    return true;
  }
}
