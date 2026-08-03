package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.identity.entity.OwnedEntity;
import com.forwardmeasure.jpa.identity.repository.JpaOwnedEntityRepository;
import jakarta.persistence.EntityManager;
import java.io.Serializable;

/**
 * CDI repositories for concrete owned entities subclass this adapter and pass
 * their entity class from an injected-constructor call.
 */
public abstract class QuarkusOwnedEntityRepository<
        T extends OwnedEntity<I>, I extends Serializable>
        extends JpaOwnedEntityRepository<T, I> {

    protected QuarkusOwnedEntityRepository(
            Class<T> entityType, EntityManager entityManager) {
        super(entityType, entityManager);
    }
}
