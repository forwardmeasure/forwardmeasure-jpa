package com.forwardmeasure.jpa.micronaut;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.jpa.asynctask.repository.AsyncTaskRepository;
import com.forwardmeasure.jpa.asynctask.service.AsyncTaskService;
import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.asynctask.service.impl.AsyncTaskServiceImpl;
import com.forwardmeasure.jpa.core.repository.AbstractBaseRepository;
import com.forwardmeasure.jpa.datasource.TenantDataSourceRegistry;
import com.forwardmeasure.jpa.datasource.TenantDataSourceTemplate;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.identity.service.impl.ActorServiceImpl;
import com.forwardmeasure.jpa.liquibase.TenantDatabaseResolver;
import com.forwardmeasure.jpa.liquibase.TenantRegistry;
import com.forwardmeasure.jpa.locking.repository.SystemLockRepository;
import com.forwardmeasure.jpa.locking.service.SystemLockService;
import com.forwardmeasure.jpa.locking.service.impl.SystemLockServiceImpl;
import com.forwardmeasure.jpa.tenancy.FunctionalSchema;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.jpa.tenancy.ThreadBoundTenantScope;
import io.micronaut.configuration.hibernate.jpa.conf.serviceregistry.builder.configures.StandardServiceRegistryBuilderConfigurer;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.annotation.Value;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

/** Registers the common JPA components without Micronaut Data repositories. */
@Factory
public class ForwardMeasureJpaFactory {

  @Singleton
  @Secondary
  TenantScope tenantScope() {
    return new ThreadBoundTenantScope();
  }

  @Singleton
  @Secondary
  CurrentTenantIdentifierResolver<String> tenantIdentifierResolver(TenantScope tenantScope) {
    return new MicronautTenantIdentifierResolver(tenantScope);
  }

  @Singleton
  @Secondary
  FunctionalSchema functionalSchema(
      @Value("${forwardmeasure.jpa.functional-schema}") String functionalSchema) {
    return FunctionalSchema.valueOf(functionalSchema.toUpperCase(Locale.ROOT));
  }

  @Singleton
  @Secondary
  @Bean(preDestroy = "close")
  TenantDataSourceRegistry tenantDataSourceRegistry(
      @Value("${forwardmeasure.jpa.tenant-database.host}") String host,
      @Value("${forwardmeasure.jpa.tenant-database.port:5432}") int port,
      @Value("${forwardmeasure.jpa.tenant-database.username}") String username,
      @Value("${forwardmeasure.jpa.tenant-database.password}") String password,
      @Value("${forwardmeasure.jpa.tenant-database.minimum-idle:}") Optional<Integer> minimumIdle,
      @Value("${forwardmeasure.jpa.tenant-database.maximum-pool-size:}")
          Optional<Integer> maximumPoolSize,
      @Value("${forwardmeasure.jpa.tenant-database.idle-eviction-timeout-minutes:}")
          Optional<Long> idleEvictionTimeoutMinutes) {
    return new TenantDataSourceRegistry(
        new TenantDataSourceTemplate(
            "jdbc:postgresql://" + host + ":" + port + "/",
            username,
            password,
            minimumIdle.orElse(TenantDataSourceTemplate.DEFAULT_MINIMUM_IDLE),
            maximumPoolSize.orElse(TenantDataSourceTemplate.DEFAULT_MAXIMUM_POOL_SIZE),
            idleEvictionTimeoutMinutes
                .map(Duration::ofMinutes)
                .orElse(TenantDataSourceTemplate.DEFAULT_IDLE_EVICTION_TIMEOUT)));
  }

  /**
   * @param dataSource the app's own default {@code DataSource} (from {@code datasources.default.*})
   *     - already used only for Hibernate's own tenant-agnostic operations (dialect resolution,
   *     never real tenant data), and reused here for exactly the same reason: it must already point
   *     at the small platform/control-plane database this class's own {@code tenant_registry} table
   *     lives in, not any tenant's own database. One connection target serves both purposes; no
   *     separate platform-database config is needed.
   */
  @Singleton
  @Secondary
  @Requires(beans = DataSource.class)
  TenantRegistry tenantRegistry(DataSource dataSource) {
    return new TenantRegistry(DelegatingDataSource.unwrapDataSource(dataSource));
  }

  @Singleton
  @Secondary
  TenantDatabaseResolver tenantDatabaseResolver(TenantRegistry registry) {
    return new TenantDatabaseResolver(registry);
  }

  /**
   * @param dataSource used only for {@link MicronautSchemaConnectionProvider#getAnyConnection()} -
   *     Hibernate's own tenant-agnostic operations, never real tenant data access. Points at an
   *     administrative database, not any tenant's own database - unchanged bean, repointed at
   *     deployment-config level, not by any code in this module.
   */
  @Singleton
  @Secondary
  @Requires(beans = DataSource.class)
  MultiTenantConnectionProvider<String> tenantConnectionProvider(
      TenantDataSourceRegistry registry, FunctionalSchema functionalSchema, DataSource dataSource) {
    return new MicronautSchemaConnectionProvider(registry, functionalSchema, dataSource);
  }

  @Singleton
  StandardServiceRegistryBuilderConfigurer tenantServiceConfigurer(
      CurrentTenantIdentifierResolver<String> tenantResolver,
      MultiTenantConnectionProvider<String> connectionProvider) {
    return (configuration, builder) -> {
      builder.addService(MultiTenantConnectionProvider.class, connectionProvider);
      builder.applySetting(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
    };
  }

  @Singleton
  @Secondary
  @Requires(beans = EntityManager.class)
  ActorRepository actorRepository(EntityManager entityManager) {
    return repository(new ActorRepository(), entityManager);
  }

  @Singleton
  @Secondary
  ActorService actorService(
      ActorRepository repository, TransactionOperations<Session> transactions) {
    return MicronautTransactionalServiceProxy.create(
        ActorService.class, new ActorServiceImpl(repository), transactions);
  }

  @Singleton
  @Secondary
  @Requires(beans = EntityManager.class)
  SystemLockRepository systemLockRepository(EntityManager entityManager) {
    return repository(new SystemLockRepository(), entityManager);
  }

  @Singleton
  @Secondary
  SystemLockService systemLockService(
      SystemLockRepository repository, TransactionOperations<Session> transactions) {
    return MicronautTransactionalServiceProxy.create(
        SystemLockService.class, new SystemLockServiceImpl(repository), transactions);
  }

  @Singleton
  @Secondary
  @Requires(beans = EntityManager.class)
  AsyncTaskRepository asyncTaskRepository(EntityManager entityManager) {
    return repository(new AsyncTaskRepository(), entityManager);
  }

  @Singleton
  @Secondary
  AsyncTaskService asyncTaskService(
      AsyncTaskRepository repository, TransactionOperations<Session> transactions) {
    return MicronautTransactionalServiceProxy.create(
        AsyncTaskService.class, new AsyncTaskServiceImpl(repository), transactions);
  }

  @Singleton
  @Secondary
  @Requires(beans = ObjectMapper.class)
  TaskStatusHandler taskStatusHandler(AsyncTaskService taskService, ObjectMapper objectMapper) {
    return new TaskStatusHandler(taskService, objectMapper);
  }

  private static <R extends AbstractBaseRepository<?, ?>> R repository(
      R repository, EntityManager context) {
    repository.bindPersistenceContext(context);
    return repository;
  }
}
