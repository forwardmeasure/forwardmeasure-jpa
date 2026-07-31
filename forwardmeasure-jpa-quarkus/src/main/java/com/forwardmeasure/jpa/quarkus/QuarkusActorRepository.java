package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.identity.repository.JpaActorRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@Dependent
@DefaultBean
public class QuarkusActorRepository extends JpaActorRepository {

    @Inject
    public QuarkusActorRepository(EntityManager entityManager) {
        super(entityManager);
    }
}
