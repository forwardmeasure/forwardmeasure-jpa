package com.forwardmeasure.jpa.spring;

import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

public final class SpringTenantIdentifierResolver
    implements CurrentTenantIdentifierResolver<String> {

  private final TenantScope tenantScope;

  public SpringTenantIdentifierResolver(TenantScope tenantScope) {
    this.tenantScope = tenantScope;
  }

  @Override
  public String resolveCurrentTenantIdentifier() {
    return tenantScope
        .current()
        .map(TenantDatabase::value)
        .orElse(TenantDatabase.UNBOUND_IDENTIFIER);
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }
}
