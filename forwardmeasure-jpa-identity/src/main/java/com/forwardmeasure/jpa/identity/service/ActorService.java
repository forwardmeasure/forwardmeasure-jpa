package com.forwardmeasure.jpa.identity.service;

import com.forwardmeasure.jpa.core.service.AbstractBaseService;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application-facing actor identity persistence operations. */
public interface ActorService extends AbstractBaseService<Actor, Long> {

  Optional<Actor> findByUuid(UUID uuid);

  Optional<Actor> findByIdentity(String identityProvider, String subjectIdentifier);

  List<Actor> findByEmail(String email);

  List<Actor> findByType(IdentityType type);

  boolean existsByIdentity(String identityProvider, String subjectIdentifier);
}
