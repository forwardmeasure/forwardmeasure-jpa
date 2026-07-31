package com.forwardmeasure.jpa.identity;

import com.forwardmeasure.jpa.core.AuditedEntity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Optional;

@MappedSuperclass
public abstract class OwnedEntity<I extends Serializable>
        extends AuditedEntity<I>
        implements OwnableResource {

    private static final long serialVersionUID = 1L;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_id",
            referencedColumnName = "id",
            nullable = false)
    private Actor owner;

    @Override
    public Actor getOwner() {
        return owner;
    }

    public void setOwner(Actor owner) {
        this.owner = owner;
    }

    public Optional<String> getOwnerSubjectIdentifier() {
        return Optional.ofNullable(owner)
                .map(Actor::getSubjectIdentifier);
    }

    public boolean hasOwner() {
        return owner != null;
    }
}
