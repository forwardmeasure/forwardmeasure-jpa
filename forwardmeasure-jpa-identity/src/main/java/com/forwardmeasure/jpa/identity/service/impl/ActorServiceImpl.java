package com.forwardmeasure.jpa.identity.service.impl;

import com.forwardmeasure.jpa.core.service.impl.AbstractBaseServiceImpl;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import com.forwardmeasure.jpa.identity.repository.ActorRepository;
import com.forwardmeasure.jpa.identity.service.ActorService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Standard-JPA actor service implementation shared by every application host. */
@Singleton
public class ActorServiceImpl extends AbstractBaseServiceImpl<Actor, Long, ActorRepository>
    implements ActorService {

  @Inject
  public ActorServiceImpl(ActorRepository repository) {
    super(repository);
  }

  @Override
  public Optional<Actor> findByUuid(UUID uuid) {
    return repository().findByUuid(uuid);
  }

  @Override
  public Optional<Actor> findByIdentity(String identityProvider, String subjectIdentifier) {
    return repository().findByIdentity(identityProvider, subjectIdentifier);
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
  public boolean existsByIdentity(String identityProvider, String subjectIdentifier) {
    return repository().existsByIdentity(identityProvider, subjectIdentifier);
  }
}
