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
package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.datasource.TenantDataSourceRegistry;
import com.forwardmeasure.jpa.datasource.TenantDataSourceTemplate;
import com.forwardmeasure.jpa.datasource.TenantPoolFactory;
import com.forwardmeasure.jpa.liquibase.TenantDatabaseResolver;
import com.forwardmeasure.jpa.liquibase.TenantRegistry;
import com.forwardmeasure.jpa.tenancy.FunctionalSchema;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.agroal.narayana.NarayanaTransactionIntegration;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class QuarkusTenantDataSourceProducer {

  @Produces
  @Singleton
  @DefaultBean
  FunctionalSchema functionalSchema(
      @ConfigProperty(name = "forwardmeasure.jpa.functional-schema") String functionalSchema) {
    return FunctionalSchema.valueOf(functionalSchema.toUpperCase(Locale.ROOT));
  }

  @Produces
  @Singleton
  @DefaultBean
  TenantDataSourceRegistry tenantDataSourceRegistry(
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.host") String host,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.port", defaultValue = "5432")
          int port,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.username") String username,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.password") String password,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.minimum-idle")
          Optional<Integer> minimumIdle,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.maximum-pool-size")
          Optional<Integer> maximumPoolSize,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.idle-eviction-timeout-minutes")
          Optional<Long> idleEvictionTimeoutMinutes,
      Instance<TransactionManager> transactionManager,
      Instance<TransactionSynchronizationRegistry> synchronizations) {
    // Quarkus runs Hibernate under JTA: it never commits or rolls back a connection itself, it
    // relies on the connection being enlisted in the JTA transaction. Plain pools would leave
    // every statement auto-committed, so a rolled-back transaction still changed the tenant
    // database. Agroal pools enlisted through Narayana behave like Quarkus's own datasources.
    TenantPoolFactory pools =
        transactionManager.isResolvable() && synchronizations.isResolvable()
            ? jtaEnlistedPools(transactionManager.get(), synchronizations.get())
            : TenantPoolFactory.HIKARI;
    return new TenantDataSourceRegistry(
        new TenantDataSourceTemplate(
            "jdbc:postgresql://" + host + ":" + port + "/",
            username,
            password,
            minimumIdle.orElse(TenantDataSourceTemplate.DEFAULT_MINIMUM_IDLE),
            maximumPoolSize.orElse(TenantDataSourceTemplate.DEFAULT_MAXIMUM_POOL_SIZE),
            idleEvictionTimeoutMinutes
                .map(Duration::ofMinutes)
                .orElse(TenantDataSourceTemplate.DEFAULT_IDLE_EVICTION_TIMEOUT)),
        pools);
  }

  static TenantPoolFactory jtaEnlistedPools(
      TransactionManager transactionManager, TransactionSynchronizationRegistry synchronizations) {
    return (database, template) -> {
      try {
        return AgroalDataSource.from(
            new AgroalDataSourceConfigurationSupplier()
                .connectionPoolConfiguration(
                    pool ->
                        pool.minSize(template.minimumIdle())
                            .initialSize(template.minimumIdle())
                            .maxSize(template.maximumPoolSize())
                            .transactionIntegration(
                                new NarayanaTransactionIntegration(
                                    transactionManager, synchronizations))
                            .connectionFactoryConfiguration(
                                factory ->
                                    factory
                                        .jdbcUrl(template.jdbcUrlPrefix() + database.value())
                                        .principal(new NamePrincipal(template.username()))
                                        .credential(new SimplePassword(template.password())))));
      } catch (SQLException failure) {
        throw new IllegalStateException(
            "Cannot create the connection pool for tenant database " + database.value(), failure);
      }
    };
  }

  void closeTenantDataSourceRegistry(@Disposes TenantDataSourceRegistry registry) {
    registry.close();
  }

  /**
   * @param dataSource the app's own default Agroal-managed {@code DataSource} (from {@code
   *     quarkus.datasource.*}) - already used only for Hibernate's own tenant-agnostic operations
   *     (dialect resolution, never real tenant data), and reused here for exactly the same reason:
   *     it must already point at the small platform/control-plane database this class's own {@code
   *     tenant_registry} table lives in, not any tenant's own database. One connection target
   *     serves both purposes; no separate platform-database config is needed.
   *     <p>Injected as {@link Instance} rather than directly: not every consumer of {@code
   *     forwardmeasure-jpa-quarkus} configures a datasource at all - some (e.g. {@code
   *     openworkflow-engine-kafka-streams-quarkus}) pull this module in only for {@code
   *     ActiveOrganizationProvider} with {@code quarkus.hibernate-orm.enabled: false} and no
   *     datasource whatsoever. A direct {@code DataSource} parameter makes Arc's build-time
   *     validation fail for those consumers even though nothing of theirs ever injects {@link
   *     TenantRegistry}/{@link TenantDatabaseResolver}. {@link Instance} is always resolvable at
   *     build time regardless of whether a {@code DataSource} bean exists; {@link Instance#get()}
   *     is only actually evaluated if this producer itself is ever invoked, which only happens for
   *     a real injection point that needs {@link TenantRegistry}.
   */
  @Produces
  @Singleton
  @DefaultBean
  TenantRegistry tenantRegistry(Instance<DataSource> dataSource) {
    return new TenantRegistry(dataSource.get());
  }

  @Produces
  @Singleton
  @DefaultBean
  TenantDatabaseResolver tenantDatabaseResolver(TenantRegistry registry) {
    return new TenantDatabaseResolver(registry);
  }
}
