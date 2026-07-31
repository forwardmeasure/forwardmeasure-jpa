package com.forwardmeasure.jpa.core;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.io.Serializable;

/**
 * Minimal provider-neutral base for JPA entities.
 *
 * <p>Equality is deliberately not implemented here. Correct entity equality
 * depends on the domain identity and on the proxy strategy of the selected JPA
 * provider; a generic implementation is more dangerous than requiring each
 * concrete domain to make that decision explicitly.
 */
@MappedSuperclass
public abstract class AbstractBaseEntity<I extends Serializable>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    public abstract I getId();

    public abstract void setId(I id);

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
