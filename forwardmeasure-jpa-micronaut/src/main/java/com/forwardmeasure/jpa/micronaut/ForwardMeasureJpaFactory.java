package com.forwardmeasure.jpa.micronaut;

import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.repository.JpaActorRepository;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.jpa.tenancy.ThreadBoundTenantScope;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.configuration.hibernate.jpa.conf.serviceregistry.builder.configures.StandardServiceRegistryBuilderConfigurer;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.cfg.AvailableSettings;

@Factory
public class ForwardMeasureJpaFactory {

    @Singleton
    @Secondary
    TenantScope tenantScope() {
        return new ThreadBoundTenantScope();
    }

    @Singleton
    @Secondary
    CurrentTenantIdentifierResolver<String> tenantIdentifierResolver(
            TenantScope tenantScope) {
        return new MicronautTenantIdentifierResolver(tenantScope);
    }

    @Singleton
    @Secondary
    MultiTenantConnectionProvider<String> tenantConnectionProvider(
            DataSource dataSource) {
        return new MicronautSchemaConnectionProvider(dataSource);
    }

    @Singleton
    StandardServiceRegistryBuilderConfigurer tenantServiceConfigurer(
            CurrentTenantIdentifierResolver<String> tenantResolver,
            MultiTenantConnectionProvider<String> connectionProvider) {
        return (configuration, builder) -> {
            builder.addService(
                    MultiTenantConnectionProvider.class,
                    connectionProvider);
            builder.applySetting(
                    AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
                    tenantResolver);
        };
    }

    @Singleton
    @Secondary
    @Requires(beans = EntityManager.class)
    ActorRepository actorRepository(EntityManager entityManager) {
        return new JpaActorRepository(entityManager);
    }
}
