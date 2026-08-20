package com.forwardmeasure.jpa.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class TenantScopeTest {

  @Test
  void derivesAndRecoversTenantSchema() {
    TenantId id = new TenantId(UUID.fromString("792a6af3-921b-4951-bd19-6c4ac82e701c"));
    TenantSchema schema = TenantSchema.forTenant(id);

    assertEquals("t_792a6af3921b4951bd196c4ac82e701c", schema.value());
    assertEquals(id, schema.tenantId());
  }

  @Test
  void rejectsArbitrarySchemaText() {
    assertThrows(
        IllegalArgumentException.class, () -> new TenantSchema("public; drop schema public"));
    assertThrows(
        IllegalArgumentException.class, () -> new TenantSchema(TenantSchema.UNBOUND_IDENTIFIER));
  }

  @Test
  void nestedScopeRestoresAndThenClearsTenant() {
    ThreadBoundTenantScope scope = new ThreadBoundTenantScope();
    TenantSchema first = TenantSchema.forTenant(new TenantId(UUID.randomUUID()));
    TenantSchema second = TenantSchema.forTenant(new TenantId(UUID.randomUUID()));

    try (TenantScope.Scope ignored = scope.open(first)) {
      assertEquals(first, scope.currentRequired());
      try (TenantScope.Scope nested = scope.open(second)) {
        assertEquals(second, scope.currentRequired());
      }
      assertEquals(first, scope.currentRequired());
    }

    assertTrue(scope.current().isEmpty());
  }

  @Test
  void scopesMustCloseInReverseOrder() {
    ThreadBoundTenantScope scope = new ThreadBoundTenantScope();
    TenantScope.Scope outer = scope.open(TenantSchema.forTenant(new TenantId(UUID.randomUUID())));
    TenantScope.Scope inner = scope.open(TenantSchema.forTenant(new TenantId(UUID.randomUUID())));

    assertThrows(IllegalStateException.class, outer::close);
    inner.close();
    outer.close();
    assertTrue(scope.current().isEmpty());
  }

  @Test
  void scopeCannotBeClosedFromAnotherThread() throws Exception {
    ThreadBoundTenantScope scope = new ThreadBoundTenantScope();
    TenantScope.Scope tenant = scope.open(TenantSchema.forTenant(new TenantId(UUID.randomUUID())));

    try (var executor = Executors.newSingleThreadExecutor()) {
      ExecutionException failure =
          assertThrows(ExecutionException.class, () -> executor.submit(tenant::close).get());
      assertTrue(failure.getCause() instanceof IllegalStateException);
    }

    tenant.close();
    assertTrue(scope.current().isEmpty());
  }
}
