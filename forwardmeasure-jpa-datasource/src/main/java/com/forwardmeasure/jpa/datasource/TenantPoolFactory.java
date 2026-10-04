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
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Builds the connection pool {@link TenantDataSourceRegistry} keeps for one tenant database. The
 * pool must also implement {@link AutoCloseable}; the registry closes it when it is evicted or the
 * registry closes.
 *
 * <p>{@link #HIKARI} suits frameworks whose transactions are local JDBC transactions (Spring,
 * Micronaut, plain Java). A framework that runs Hibernate under JTA (Quarkus) must supply pools
 * enlisted in its transaction manager: otherwise every statement auto-commits and a rollback leaves
 * the tenant database changed.
 */
@FunctionalInterface
public interface TenantPoolFactory {

  /** Small Hikari pools, connections opened lazily on first borrow. */
  TenantPoolFactory HIKARI =
      (database, template) -> {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(template.jdbcUrlPrefix() + database.value());
        config.setUsername(template.username());
        config.setPassword(template.password());
        config.setMinimumIdle(template.minimumIdle());
        config.setMaximumPoolSize(template.maximumPoolSize());
        config.setPoolName("tenant-" + database.value());
        // A tenant's database may not be reachable/provisioned at the instant this pool object is
        // created - connections are still attempted lazily on first real borrow.
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
      };

  /**
   * Builds the pool for {@code database} from {@code template}; it must be {@link AutoCloseable}.
   */
  DataSource create(TenantDatabase database, TenantDataSourceTemplate template);
}
