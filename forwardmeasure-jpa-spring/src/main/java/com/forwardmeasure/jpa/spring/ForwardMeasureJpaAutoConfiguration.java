package com.forwardmeasure.jpa.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.jpa.asynctask.repository.AsyncTaskRepository;
import com.forwardmeasure.jpa.asynctask.service.AsyncTaskService;
import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.asynctask.service.impl.AsyncTaskServiceImpl;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.identity.service.impl.ActorServiceImpl;
import com.forwardmeasure.jpa.locking.repository.SystemLockRepository;
import com.forwardmeasure.jpa.locking.service.SystemLockService;
import com.forwardmeasure.jpa.locking.service.impl.SystemLockServiceImpl;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.jpa.tenancy.ThreadBoundTenantScope;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
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

/** Registers the common JPA components without Spring-specific repositories. */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
@ConditionalOnSingleCandidate(DataSource.class)
public class ForwardMeasureJpaAutoConfiguration {

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
  MultiTenantConnectionProvider<String> forwardMeasureConnectionProvider(DataSource dataSource) {
    return new SpringSchemaConnectionProvider(dataSource);
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
