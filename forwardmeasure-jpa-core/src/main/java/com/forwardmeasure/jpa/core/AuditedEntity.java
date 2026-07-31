package com.forwardmeasure.jpa.core;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A resource with an immutable public UUID and database-managed lifecycle
 * timestamps.
 *
 * <p>"Audited" here means persistent lifecycle metadata. Acting-principal and
 * business-event audit history belong in an append-only audit facility, not in
 * recursive JPA relationships on this base class.
 */
@MappedSuperclass
public abstract class AuditedEntity<I extends Serializable>
        extends AbstractBaseEntity<I>
        implements AuthorizableResource {

    private static final long serialVersionUID = 1L;

    @NotNull
    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void initializeAuditFields() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void updateAuditTimestamp() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @Override
    public String getResourceId() {
        return uuid == null ? null : uuid.toString();
    }

    @Override
    public String getResourceType() {
        return getClass().getSimpleName();
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
