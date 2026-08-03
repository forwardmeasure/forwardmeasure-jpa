package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.core.query.JpaSpecification;
import com.forwardmeasure.jpa.core.query.Page;
import com.forwardmeasure.jpa.core.query.PageRequest;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import com.forwardmeasure.jpa.identity.service.RepositoryActorService;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Default transactional Quarkus actor service. */
@ApplicationScoped
@DefaultBean
@Transactional
public class QuarkusActorService implements ActorService {

    private final RepositoryActorService delegate;

    @Inject
    public QuarkusActorService(ActorRepository repository) {
        this.delegate = new RepositoryActorService(repository);
    }

    @Override
    public Actor save(Actor actor) {
        return delegate.save(actor);
    }

    @Override
    public Actor saveAndFlush(Actor actor) {
        return delegate.saveAndFlush(actor);
    }

    @Override
    public Optional<Actor> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public List<Actor> findAll() {
        return delegate.findAll();
    }

    @Override
    public Page<Actor> findAll(PageRequest pageRequest) {
        return delegate.findAll(pageRequest);
    }

    @Override
    public Page<Actor> findAll(
            PageRequest pageRequest,
            JpaSpecification<Actor> specification) {
        return delegate.findAll(pageRequest, specification);
    }

    @Override
    public long count() {
        return delegate.count();
    }

    @Override
    public boolean deleteById(Long id) {
        return delegate.deleteById(id);
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void detach(Actor actor) {
        delegate.detach(actor);
    }

    @Override
    public Optional<Actor> findByUuid(UUID uuid) {
        return delegate.findByUuid(uuid);
    }

    @Override
    public Optional<Actor> findByIdentity(
            String identityProvider,
            String subjectIdentifier) {
        return delegate.findByIdentity(identityProvider, subjectIdentifier);
    }

    @Override
    public List<Actor> findByEmail(String email) {
        return delegate.findByEmail(email);
    }

    @Override
    public List<Actor> findByType(IdentityType type) {
        return delegate.findByType(type);
    }

    @Override
    public boolean existsByIdentity(
            String identityProvider,
            String subjectIdentifier) {
        return delegate.existsByIdentity(identityProvider, subjectIdentifier);
    }
}
