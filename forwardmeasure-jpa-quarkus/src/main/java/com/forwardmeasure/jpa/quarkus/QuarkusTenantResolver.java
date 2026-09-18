package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Connects the explicit ForwardMeasure tenant scope to Quarkus Hibernate ORM. An unscoped operation
 * fails closed instead of silently using a real tenant's own database.
 */
@PersistenceUnitExtension
@ApplicationScoped
public class QuarkusTenantResolver implements TenantResolver {

  private final TenantScope tenantScope;

  @Inject
  public QuarkusTenantResolver(TenantScope tenantScope) {
    this.tenantScope = tenantScope;
  }

  @Override
  public String getDefaultTenantId() {
    return TenantDatabase.UNBOUND_IDENTIFIER;
  }

  @Override
  public String resolveTenantId() {
    return tenantScope.currentRequired().value();
  }
}
