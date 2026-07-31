package com.forwardmeasure.jpa.spring;

import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.repository.JpaActorRepository;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.jpa.tenancy.ThreadBoundTenantScope;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

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
    MultiTenantConnectionProvider<String> forwardMeasureConnectionProvider(
            DataSource dataSource) {
        return new SpringSchemaConnectionProvider(dataSource);
    }

    @Bean
    HibernatePropertiesCustomizer forwardMeasureHibernateProperties(
            CurrentTenantIdentifierResolver<String> tenantResolver,
            MultiTenantConnectionProvider<String> connectionProvider) {
        return properties -> {
            properties.put(
                    AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
                    tenantResolver);
            properties.put(
                    AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER,
                    connectionProvider);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    ActorRepository forwardMeasureActorRepository(
            EntityManager entityManager) {
        return new JpaActorRepository(entityManager);
    }
}
