package com.forwardmeasure.jpa.liquibase;

import com.forwardmeasure.jpa.tenancy.TenantSchema;

public final class TenantMigrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TenantMigrationException(
            TenantSchema schema, Throwable cause) {
        super("Failed to migrate tenant schema " + schema.value(), cause);
    }
}
