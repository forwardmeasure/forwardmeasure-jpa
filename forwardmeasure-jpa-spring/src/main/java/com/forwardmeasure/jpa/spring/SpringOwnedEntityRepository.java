package com.forwardmeasure.jpa.spring;

import com.forwardmeasure.jpa.identity.OwnedEntity;
import com.forwardmeasure.jpa.identity.repository.JpaOwnedEntityRepository;
import jakarta.persistence.EntityManager;
import java.io.Serializable;

/**
 * Base for applications preferring the provider-neutral repository contract
 * over Spring Data repository interfaces.
 */
public abstract class SpringOwnedEntityRepository<
        T extends OwnedEntity<I>, I extends Serializable>
        extends JpaOwnedEntityRepository<T, I> {

    protected SpringOwnedEntityRepository(
            Class<T> entityType, EntityManager entityManager) {
        super(entityType, entityManager);
    }
}
