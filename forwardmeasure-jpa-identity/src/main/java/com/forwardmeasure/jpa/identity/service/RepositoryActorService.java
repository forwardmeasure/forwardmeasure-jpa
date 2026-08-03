package com.forwardmeasure.jpa.identity.service;

import com.forwardmeasure.jpa.core.service.AbstractEntityService;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Provider-neutral actor service backed by an {@link ActorRepository}. */
public class RepositoryActorService
        extends AbstractEntityService<Actor, Long, ActorRepository>
        implements ActorService {

    public RepositoryActorService(ActorRepository repository) {
        super(repository);
    }

    @Override
    public Optional<Actor> findByUuid(UUID uuid) {
        return repository().findByUuid(uuid);
    }

    @Override
    public Optional<Actor> findByIdentity(
            String identityProvider,
            String subjectIdentifier) {
        return repository().findByIdentity(
                identityProvider,
                subjectIdentifier);
    }

    @Override
    public List<Actor> findByEmail(String email) {
        return repository().findByEmail(email);
    }

    @Override
    public List<Actor> findByType(IdentityType type) {
        return repository().findByType(type);
    }

    @Override
    public boolean existsByIdentity(
            String identityProvider,
            String subjectIdentifier) {
        return repository().existsByIdentity(
                identityProvider,
                subjectIdentifier);
    }
}
