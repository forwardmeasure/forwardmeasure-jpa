package com.forwardmeasure.jpa.spring;

import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.RepositoryActorService;
import org.springframework.transaction.annotation.Transactional;

/** Default transactional Spring actor service. */
@Transactional
public class SpringActorService extends RepositoryActorService {

    public SpringActorService(ActorRepository repository) {
        super(repository);
    }
}
