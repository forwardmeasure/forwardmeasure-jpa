package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.jpa.tenancy.ThreadBoundTenantScope;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class QuarkusTenantScopeProducer {

    @Produces
    @Singleton
    @DefaultBean
    TenantScope tenantScope() {
        return new ThreadBoundTenantScope();
    }
}
