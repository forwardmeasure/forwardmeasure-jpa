package com.forwardmeasure.jpa.micronaut;

import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.RepositoryActorService;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/** Default transactional Micronaut actor service. */
@Singleton
@Secondary
@Transactional
public class MicronautActorService extends RepositoryActorService {

    public MicronautActorService(ActorRepository repository) {
        super(repository);
    }
}
