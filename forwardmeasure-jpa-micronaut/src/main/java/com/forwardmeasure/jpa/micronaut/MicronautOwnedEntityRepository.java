package com.forwardmeasure.jpa.micronaut;

import com.forwardmeasure.jpa.identity.OwnedEntity;
import com.forwardmeasure.jpa.identity.repository.JpaOwnedEntityRepository;
import jakarta.persistence.EntityManager;
import java.io.Serializable;

public abstract class MicronautOwnedEntityRepository<
        T extends OwnedEntity<I>, I extends Serializable>
        extends JpaOwnedEntityRepository<T, I> {

    protected MicronautOwnedEntityRepository(
            Class<T> entityType, EntityManager entityManager) {
        super(entityType, entityManager);
    }
}
