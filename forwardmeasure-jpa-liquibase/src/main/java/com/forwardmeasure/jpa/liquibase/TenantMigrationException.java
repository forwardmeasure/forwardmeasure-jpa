package com.forwardmeasure.jpa.liquibase;

import com.forwardmeasure.jpa.tenancy.TenantSchema;

public final class TenantMigrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TenantMigrationException(
            TenantSchema schema, Throwable cause) {
        this(schema, "migrate", cause);
    }

    public TenantMigrationException(
            TenantSchema schema, String operation, Throwable cause) {
        super("Failed to " + operation + " tenant schema " + schema.value(), cause);
    }
}
