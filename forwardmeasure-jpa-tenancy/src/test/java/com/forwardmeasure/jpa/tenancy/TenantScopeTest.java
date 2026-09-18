package com.forwardmeasure.jpa.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class TenantScopeTest {

  @Test
  void nestedScopeRestoresAndThenClearsTenant() {
    ThreadBoundTenantScope scope = new ThreadBoundTenantScope();
    TenantDatabase first = TenantDatabase.forAlias("lux");
    TenantDatabase second = TenantDatabase.forAlias("acme");

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
    TenantScope.Scope outer = scope.open(TenantDatabase.forAlias("lux"));
    TenantScope.Scope inner = scope.open(TenantDatabase.forAlias("acme"));

    assertThrows(IllegalStateException.class, outer::close);
    inner.close();
    outer.close();
    assertTrue(scope.current().isEmpty());
  }

  @Test
  void scopeCannotBeClosedFromAnotherThread() throws Exception {
    ThreadBoundTenantScope scope = new ThreadBoundTenantScope();
    TenantScope.Scope tenant = scope.open(TenantDatabase.forAlias("lux"));

    try (var executor = Executors.newSingleThreadExecutor()) {
      ExecutionException failure =
          assertThrows(ExecutionException.class, () -> executor.submit(tenant::close).get());
      assertTrue(failure.getCause() instanceof IllegalStateException);
    }

    tenant.close();
    assertTrue(scope.current().isEmpty());
  }
}
