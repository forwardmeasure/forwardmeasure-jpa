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

import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * A synchronous transaction scope whose private PostgreSQL pool has a server deadline from
 * connection startup, before BEGIN. Closing discards that pool; ordinary pools keep their settings.
 * Callers must close this scope only after their transaction has committed or rolled back.
 */
public final class TransactionDeadlineScope implements AutoCloseable {
  private static final ThreadLocal<TransactionDeadlineScope> CURRENT = new ThreadLocal<>();
  private final TenantDatabase database;
  private final Duration timeout;
  private final String applicationName = "fwd-deadline-" + UUID.randomUUID();
  private final Map<TenantDataSourceRegistry, DataSource> pools = new IdentityHashMap<>();
  private boolean closed;

  private TransactionDeadlineScope(TenantDatabase database, Duration timeout) {
    this.database = Objects.requireNonNull(database, "database");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    if (timeout.toMillis() < 1 || timeout.compareTo(Duration.ofHours(1)) > 0)
      throw new IllegalArgumentException(
          "Transaction deadline must be between one millisecond and one hour");
  }

  public static TransactionDeadlineScope open(TenantDatabase database, Duration timeout) {
    if (CURRENT.get() != null)
      throw new IllegalStateException("A transaction deadline scope is already active");
    TransactionDeadlineScope scope = new TransactionDeadlineScope(database, timeout);
    CURRENT.set(scope);
    return scope;
  }

  static DataSource currentPool(
      TenantDataSourceRegistry registry,
      TenantDatabase database,
      TenantPoolFactory factory,
      TenantDataSourceTemplate template) {
    TransactionDeadlineScope scope = CURRENT.get();
    if (scope == null || !scope.database.equals(database)) return null;
    if (scope.closed) throw new IllegalStateException("Transaction deadline scope is closed");
    return scope.pools.computeIfAbsent(
        registry,
        ignored -> {
          Map<String, String> properties = new LinkedHashMap<>(template.connectionProperties());
          String existingOptions = properties.getOrDefault("options", "");
          properties.put(
              "options",
              existingOptions + " -c transaction_timeout=" + scope.timeout.toMillis() + "ms");
          properties.put("ApplicationName", scope.applicationName);
          TenantDataSourceTemplate deadlineTemplate =
              new TenantDataSourceTemplate(
                  template.jdbcUrlPrefix(),
                  template.username(),
                  template.password(),
                  0,
                  1,
                  template.idleEvictionTimeout(),
                  properties);
          return factory.create(database, deadlineTemplate);
        });
  }

  @Override
  public void close() {
    if (closed) return;
    if (CURRENT.get() != this)
      throw new IllegalStateException(
          "Transaction deadline scope must close on its opening thread");
    CURRENT.remove();
    closed = true;
    RuntimeException failure = null;
    for (DataSource pool : pools.values()) {
      try {
        if (!(pool instanceof AutoCloseable closeable))
          throw new IllegalStateException("A transaction deadline pool must be closeable");
        closeable.close();
      } catch (Exception exception) {
        if (failure == null)
          failure =
              new IllegalStateException("Could not discard a transaction deadline pool", exception);
        else failure.addSuppressed(exception);
      }
    }
    pools.clear();
    if (failure != null) throw failure;
  }
}
