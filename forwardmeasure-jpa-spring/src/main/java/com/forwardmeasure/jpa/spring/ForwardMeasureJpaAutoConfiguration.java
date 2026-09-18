package com.forwardmeasure.jpa.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.jpa.asynctask.repository.AsyncTaskRepository;
import com.forwardmeasure.jpa.asynctask.service.AsyncTaskService;
import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.asynctask.service.impl.AsyncTaskServiceImpl;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/** Registers the common JPA components without Spring-specific repositories. */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
@ConditionalOnSingleCandidate(DataSource.class)
public class ForwardMeasureJpaAutoConfiguration {

  // Verified live (2026-08-28, forwardmeasure-agent-os): spring-boot-starter-data-jpa registers
  // EntityManagerFactory only - a directly injectable EntityManager bean only exists via
  // @PersistenceContext field/method injection, which a @Bean factory-method parameter is not.
  // Quarkus's ArC and Micronaut's DI container both register EntityManager as a real bean type as
  // part of their own Hibernate ORM extensions (confirmed: neither needs this), so this is a
  // Spring-only gap. Every @Bean method below that takes an EntityManager parameter - including
  // this class's own forwardMeasureActorRepository et al. - was silently unsatisfiable without it.
  // @ConditionalOnMissingBean so an application defining its own EntityManager bean is untouched.
  @Bean
  @ConditionalOnMissingBean(EntityManager.class)
  EntityManager forwardMeasureEntityManager(EntityManagerFactory entityManagerFactory) {
    return SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  TenantScope forwardMeasureTenantScope() {
    return new ThreadBoundTenantScope();
  }

  @Bean
  @ConditionalOnMissingBean
  CurrentTenantIdentifierResolver<String> forwardMeasureTenantIdentifierResolver(
      TenantScope tenantScope) {
    return new SpringTenantIdentifierResolver(tenantScope);
  }

  @Bean
  @ConditionalOnMissingBean
  FunctionalSchema forwardMeasureFunctionalSchema(
      @Value("${forwardmeasure.jpa.functional-schema}") String functionalSchema) {
    return FunctionalSchema.valueOf(functionalSchema.toUpperCase(Locale.ROOT));
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  TenantDataSourceRegistry forwardMeasureTenantDataSourceRegistry(
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
   * @param dataSource the app's own configured {@code DataSource} (from {@code
   *     spring.datasource.*}) - already used only for Hibernate's own tenant-agnostic operations
   *     (dialect resolution, never real tenant data), and reused here for exactly the same reason:
   *     it must already point at the small platform/control-plane database this class's own {@code
   *     tenant_registry} table lives in, not any tenant's own database. One connection target
   *     serves both purposes; no separate platform-database config is needed.
   */
  @Bean
  @ConditionalOnMissingBean
  TenantRegistry forwardMeasureTenantRegistry(DataSource dataSource) {
    return new TenantRegistry(dataSource);
  }

  @Bean
  @ConditionalOnMissingBean
  TenantDatabaseResolver forwardMeasureTenantDatabaseResolver(TenantRegistry registry) {
    return new TenantDatabaseResolver(registry);
  }

  /**
   * @param dataSource used only for {@link SpringSchemaConnectionProvider#getAnyConnection()} -
   *     Hibernate's own tenant-agnostic operations, never real tenant data access. Points at an
   *     administrative database, not any tenant's own database - unchanged bean, repointed at
   *     deployment-config level, not by any code in this module.
   */
  @Bean
  @ConditionalOnMissingBean
  MultiTenantConnectionProvider<String> forwardMeasureConnectionProvider(
      TenantDataSourceRegistry registry, FunctionalSchema functionalSchema, DataSource dataSource) {
    return new SpringSchemaConnectionProvider(registry, functionalSchema, dataSource);
  }

  @Bean
  HibernatePropertiesCustomizer forwardMeasureHibernateProperties(
      CurrentTenantIdentifierResolver<String> tenantResolver,
      MultiTenantConnectionProvider<String> connectionProvider) {
    return properties -> {
      properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
      properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
    };
  }

  @Bean
  @ConditionalOnMissingBean
  ActorRepository forwardMeasureActorRepository(EntityManager entityManager) {
    return repository(new ActorRepository(), entityManager);
  }

  @Bean
  @ConditionalOnMissingBean
  ActorService forwardMeasureActorService(ActorRepository repository) {
    return new ActorServiceImpl(repository);
  }

  @Bean
  @ConditionalOnMissingBean
  SystemLockRepository forwardMeasureSystemLockRepository(EntityManager entityManager) {
    return repository(new SystemLockRepository(), entityManager);
  }

  @Bean
  @ConditionalOnMissingBean
  SystemLockService forwardMeasureSystemLockService(SystemLockRepository repository) {
    return new SystemLockServiceImpl(repository);
  }

  @Bean
  @ConditionalOnMissingBean
  AsyncTaskRepository forwardMeasureAsyncTaskRepository(EntityManager entityManager) {
    return repository(new AsyncTaskRepository(), entityManager);
  }

  @Bean
  @ConditionalOnMissingBean
  AsyncTaskService forwardMeasureAsyncTaskService(AsyncTaskRepository repository) {
    return new AsyncTaskServiceImpl(repository);
  }

  @Bean
  @ConditionalOnBean(ObjectMapper.class)
  @ConditionalOnMissingBean
  TaskStatusHandler forwardMeasureTaskStatusHandler(
      AsyncTaskService taskService, ObjectMapper objectMapper) {
    return new TaskStatusHandler(taskService, objectMapper);
  }

  private static <R extends com.forwardmeasure.jpa.core.repository.AbstractBaseRepository<?, ?>>
      R repository(R repository, EntityManager context) {
    repository.bindPersistenceContext(context);
    return repository;
  }
}
