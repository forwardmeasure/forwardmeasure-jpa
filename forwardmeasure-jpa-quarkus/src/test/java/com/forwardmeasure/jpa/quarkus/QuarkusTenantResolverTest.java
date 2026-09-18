package com.forwardmeasure.jpa.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forwardmeasure.jpa.tenancy.TenantDatabase;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.jpa.tenancy.ThreadBoundTenantScope;
import org.junit.jupiter.api.Test;

class QuarkusTenantResolverTest {

  @Test
  void defaultsToPublicAndFailsClosedWithoutAnExplicitScope() {
    QuarkusTenantResolver resolver = new QuarkusTenantResolver(new ThreadBoundTenantScope());

    assertEquals(TenantDatabase.UNBOUND_IDENTIFIER, resolver.getDefaultTenantId());
    assertThrows(IllegalStateException.class, resolver::resolveTenantId);
  }

  @Test
  void resolvesTheExplicitTenantScope() {
    TenantScope scope = new ThreadBoundTenantScope();
    TenantDatabase tenant = TenantDatabase.forAlias("resolvertest");
    QuarkusTenantResolver resolver = new QuarkusTenantResolver(scope);

    try (TenantScope.Scope ignored = scope.open(tenant)) {
      assertEquals(tenant.value(), resolver.resolveTenantId());
    }
  }
}
