package com.forwardmeasure.jpa.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.jpa.tenancy.TenantScope;
import com.forwardmeasure.jpa.tenancy.ThreadBoundTenantScope;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuarkusTenantResolverTest {

    @Test
    void defaultsToPublicAndFailsClosedWithoutAnExplicitScope() {
        QuarkusTenantResolver resolver =
                new QuarkusTenantResolver(new ThreadBoundTenantScope());

        assertEquals(TenantSchema.PUBLIC.value(), resolver.getDefaultTenantId());
        assertThrows(IllegalStateException.class, resolver::resolveTenantId);
    }

    @Test
    void resolvesTheExplicitTenantScope() {
        TenantScope scope = new ThreadBoundTenantScope();
        TenantSchema tenant = TenantSchema.forTenant(
                new TenantId(UUID.randomUUID()));
        QuarkusTenantResolver resolver = new QuarkusTenantResolver(scope);

        try (TenantScope.Scope ignored = scope.open(tenant)) {
            assertEquals(tenant.value(), resolver.resolveTenantId());
        }
    }
}
