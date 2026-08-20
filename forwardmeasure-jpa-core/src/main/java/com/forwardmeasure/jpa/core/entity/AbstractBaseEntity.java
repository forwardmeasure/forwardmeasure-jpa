package com.forwardmeasure.jpa.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.io.Serializable;
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
public abstract class AbstractBaseEntity<I extends Serializable> implements Serializable {

  protected static final long serialVersionUID = 5164677531140447087L;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version;

  public abstract I getId();

  public abstract void setId(I id);

  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AbstractBaseEntity<?> otherEntity)
        || !getClass().equals(other.getClass())) {
      return false;
    }
    return getId() != null && otherEntity.getId() != null && getId().equals(otherEntity.getId());
  }

  @Override
  public final int hashCode() {
    return getId() == null ? 0 : getId().hashCode();
  }
}
