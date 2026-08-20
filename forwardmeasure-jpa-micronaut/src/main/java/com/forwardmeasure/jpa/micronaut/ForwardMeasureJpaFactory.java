package com.forwardmeasure.jpa.micronaut;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.jpa.asynctask.repository.AsyncTaskRepository;
import com.forwardmeasure.jpa.asynctask.service.AsyncTaskService;
import com.forwardmeasure.jpa.asynctask.service.TaskStatusHandler;
import com.forwardmeasure.jpa.asynctask.service.impl.AsyncTaskServiceImpl;
import com.forwardmeasure.jpa.core.repository.AbstractBaseRepository;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.identity.service.impl.ActorServiceImpl;
import com.forwardmeasure.jpa.locking.repository.SystemLockRepository;
import com.forwardmeasure.jpa.locking.service.SystemLockService;
import com.forwardmeasure.jpa.locking.service.impl.SystemLockServiceImpl;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.jpa.tenancy.ThreadBoundTenantScope;
import io.micronaut.configuration.hibernate.jpa.conf.serviceregistry.builder.configures.StandardServiceRegistryBuilderConfigurer;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
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
  MultiTenantConnectionProvider<String> tenantConnectionProvider(DataSource dataSource) {
    return new MicronautSchemaConnectionProvider(dataSource);
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
