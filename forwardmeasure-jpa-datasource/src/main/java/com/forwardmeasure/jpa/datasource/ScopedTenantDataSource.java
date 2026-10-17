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
package com.forwardmeasure.jpa.datasource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Stable provider handle; a cached Hibernate provider also honors a later deadline scope. */
final class ScopedTenantDataSource implements DataSource {
  private final Supplier<DataSource> current;

  ScopedTenantDataSource(Supplier<DataSource> current) {
    this.current = current;
  }

  @Override
  public Connection getConnection() throws SQLException {
    return current.get().getConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return current.get().getConnection(username, password);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return current.get().getLogWriter();
  }

  @Override
  public void setLogWriter(PrintWriter writer) throws SQLException {
    current.get().setLogWriter(writer);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return current.get().getLoginTimeout();
  }

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {
    current.get().setLoginTimeout(seconds);
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return current.get().getParentLogger();
  }

  @Override
  public boolean isWrapperFor(Class<?> type) throws SQLException {
    return type.isInstance(this) || current.get().isWrapperFor(type);
  }

  @Override
  public <T> T unwrap(Class<T> type) throws SQLException {
    return type.isInstance(this) ? type.cast(this) : current.get().unwrap(type);
  }
}
