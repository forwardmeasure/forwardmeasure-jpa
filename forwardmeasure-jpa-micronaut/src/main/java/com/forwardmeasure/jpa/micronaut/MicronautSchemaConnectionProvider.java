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
package com.forwardmeasure.jpa.micronaut;

import com.forwardmeasure.jpa.datasource.TenantDataSourceRegistry;
import com.forwardmeasure.jpa.liquibase.TenantDatabaseResolver;
import com.forwardmeasure.jpa.tenancy.FunctionalSchema;
import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantId;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

/** Resolves a trusted tenant ID through the registry, then opens its dedicated database pool. */
public final class MicronautSchemaConnectionProvider
    implements MultiTenantConnectionProvider<String> {

  private static final long serialVersionUID = 1L;

  private final TenantDataSourceRegistry registry;
  private final TenantDatabaseResolver resolver;
  private final FunctionalSchema schema;
  private final DataSource bootstrapDataSource;

  /**
   * @param bootstrapDataSource used only for {@link #getAnyConnection()} - Hibernate's own
   *     tenant-agnostic operations (dialect resolution, DDL export tooling), never real tenant data
   *     access. Points at an administrative database, not any tenant's own database.
   */
  public MicronautSchemaConnectionProvider(
      TenantDataSourceRegistry registry,
      TenantDatabaseResolver resolver,
      FunctionalSchema schema,
      DataSource bootstrapDataSource) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.schema = Objects.requireNonNull(schema, "schema");
    this.bootstrapDataSource =
        DelegatingDataSource.unwrapDataSource(
            Objects.requireNonNull(bootstrapDataSource, "bootstrapDataSource"));
  }

  @Override
  public Connection getAnyConnection() throws SQLException {
    return bootstrapDataSource.getConnection();
  }

  @Override
  public void releaseAnyConnection(Connection connection) throws SQLException {
    connection.close();
  }

  @Override
  public Connection getConnection(String tenantIdentifier) throws SQLException {
    TenantDatabase database = resolver.resolve(TenantId.parse(tenantIdentifier));
    Connection connection = registry.dataSourceFor(database).getConnection();
    try {
      connection.setSchema(schema.schemaName());
      return connection;
    } catch (SQLException exception) {
      connection.close();
      throw exception;
    }
  }

  @Override
  public void releaseConnection(String tenantIdentifier, Connection connection)
      throws SQLException {
    connection.close();
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
