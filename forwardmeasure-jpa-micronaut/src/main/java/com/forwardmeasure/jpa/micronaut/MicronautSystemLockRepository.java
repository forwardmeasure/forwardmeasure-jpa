package com.forwardmeasure.jpa.micronaut;

import com.forwardmeasure.jpa.locking.JpaSystemLockRepository;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;

/** Default standard-JPA lock repository for Micronaut. */
@Singleton
@Secondary
@Requires(beans = EntityManager.class)
public class MicronautSystemLockRepository extends JpaSystemLockRepository {

    public MicronautSystemLockRepository(EntityManager entityManager) {
        super(entityManager);
    }
}
