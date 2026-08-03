package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.locking.JpaSystemLockRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** Default standard-JPA lock repository for Quarkus. */
@Dependent
@DefaultBean
public class QuarkusSystemLockRepository extends JpaSystemLockRepository {

    @Inject
    public QuarkusSystemLockRepository(EntityManager entityManager) {
        super(entityManager);
    }
}
