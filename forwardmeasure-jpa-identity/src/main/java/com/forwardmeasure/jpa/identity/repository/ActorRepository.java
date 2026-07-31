package com.forwardmeasure.jpa.identity.repository;

import com.forwardmeasure.jpa.core.repository.EntityRepository;
import com.forwardmeasure.jpa.identity.Actor;
import com.forwardmeasure.jpa.identity.IdentityType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActorRepository extends EntityRepository<Actor, Long> {

    Optional<Actor> findByUuid(UUID uuid);

    Optional<Actor> findByIdentity(
            String identityProvider, String subjectIdentifier);

    List<Actor> findByEmail(String email);

    List<Actor> findByType(IdentityType type);

    boolean existsByIdentity(
            String identityProvider, String subjectIdentifier);
}
