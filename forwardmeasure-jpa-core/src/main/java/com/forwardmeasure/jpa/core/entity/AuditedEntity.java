package com.forwardmeasure.jpa.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A resource with an immutable public UUID and database-managed lifecycle
 * timestamps.
 *
 * <p>"Audited" here means persistent lifecycle metadata. Acting-principal and
 * business-event audit history belong in an append-only audit facility, not in
 * recursive JPA relationships on this base class.
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AuditedEntity<I extends Serializable>
        extends AbstractBaseEntity<I> {

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

}
