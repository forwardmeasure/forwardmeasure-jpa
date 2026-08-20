package com.forwardmeasure.jpa.identity.entity;

import com.forwardmeasure.jpa.core.entity.AuditedEntity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class OwnedEntity<I extends Serializable> extends AuditedEntity<I> {

  private static final long serialVersionUID = 1L;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", referencedColumnName = "id", nullable = false)
  private Actor owner;

  public Optional<String> getOwnerSubjectIdentifier() {
    return Optional.ofNullable(owner).map(Actor::getSubjectIdentifier);
  }

  public boolean hasOwner() {
    return owner != null;
  }
}
