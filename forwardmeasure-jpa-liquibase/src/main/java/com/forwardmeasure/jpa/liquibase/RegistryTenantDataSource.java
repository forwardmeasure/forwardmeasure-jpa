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
package com.forwardmeasure.jpa.liquibase;

import com.forwardmeasure.jpa.datasource.TenantDataSourceRegistry;
import com.forwardmeasure.jpa.tenancy.TenantId;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Re-resolves an ACTIVE tenant on every connection borrow; does not retain an evictable pool. */
public final class RegistryTenantDataSource implements DataSource {
  private final TenantDatabaseResolver resolver;
  private final TenantDataSourceRegistry pools;
  private final TenantId tenant;

  public RegistryTenantDataSource(
      TenantRegistry registry, TenantDataSourceRegistry pools, TenantId tenant) {
    this.resolver = new TenantDatabaseResolver(Objects.requireNonNull(registry));
    this.pools = Objects.requireNonNull(pools);
    this.tenant = Objects.requireNonNull(tenant);
  }

  private DataSource current() {
    return pools.dataSourceFor(resolver.resolve(tenant));
  }

  @Override
  public Connection getConnection() throws SQLException {
    return current().getConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return current().getConnection(username, password);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return current().getLogWriter();
  }

  @Override
  public void setLogWriter(PrintWriter writer) throws SQLException {
    current().setLogWriter(writer);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return current().getLoginTimeout();
  }

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {
    current().setLoginTimeout(seconds);
  }

  @Override
  public Logger getParentLogger() {
    return Logger.getLogger(RegistryTenantDataSource.class.getName());
  }

  @Override
  public boolean isWrapperFor(Class<?> type) {
    return type.isInstance(this);
  }

  @Override
  public <T> T unwrap(Class<T> type) throws SQLException {
    if (type.isInstance(this)) return type.cast(this);
    throw new SQLException("Unwrapping the tenant routing boundary is not supported");
  }
}
