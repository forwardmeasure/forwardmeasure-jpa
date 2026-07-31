package com.forwardmeasure.jpa.tenancy;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validated schema identifier. Validation is intentionally strict so a schema
 * name can be passed to JDBC's schema API without becoming an SQL-injection
 * surface.
 */
public record TenantSchema(String value) {

    /**
     * Hibernate integrations may need an identifier while bootstrapping
     * repositories before an execution scope exists. This value deliberately
     * fails {@link TenantSchema} validation, so it can never be used to obtain
     * a tenant connection.
     */
    public static final String UNBOUND_IDENTIFIER =
            "forwardmeasure_unbound_tenant";
    public static final TenantSchema PUBLIC = new TenantSchema("public");
    private static final Pattern TENANT_SCHEMA =
            Pattern.compile("t_[0-9a-f]{32}");

    public TenantSchema {
        Objects.requireNonNull(value, "value");
        value = value.toLowerCase(Locale.ROOT);
        if (!"public".equals(value)
                && !TENANT_SCHEMA.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Tenant schema must be public or t_{32 lowercase hex characters}");
        }
    }

    public static TenantSchema forTenant(TenantId tenantId) {
        return new TenantSchema(
                "t_" + tenantId.value().toString().replace("-", ""));
    }

    public TenantId tenantId() {
        if (this.equals(PUBLIC)) {
            throw new IllegalStateException(
                    "The public schema does not identify a tenant");
        }
        String hex = value.substring(2);
        return new TenantId(UUID.fromString(
                hex.substring(0, 8)
                        + "-"
                        + hex.substring(8, 12)
                        + "-"
                        + hex.substring(12, 16)
                        + "-"
                        + hex.substring(16, 20)
                        + "-"
                        + hex.substring(20)));
    }

    @Override
    public String toString() {
        return value;
    }
}
