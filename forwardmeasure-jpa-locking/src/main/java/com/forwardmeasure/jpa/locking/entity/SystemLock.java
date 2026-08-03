package com.forwardmeasure.jpa.locking.entity;

import com.forwardmeasure.jpa.core.entity.AbstractBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A named, transaction-scoped database mutex.
 *
 * <p>Rows are provisioned by application migrations. Applications must never
 * create or delete lock rows at runtime.
 */
@Entity
@Table(name = "system_lock")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemLock extends AbstractBaseEntity<String> {

    private static final long serialVersionUID = 1L;

    @Id
    @NotBlank
    @Column(name = "lock_name", nullable = false, length = 256)
    private String lockName;

    @Column(name = "description", length = 512)
    private String description;

    @Override
    public String getId() {
        return lockName;
    }

    @Override
    public void setId(String id) {
        lockName = id;
    }

}
