package com.forwardmeasure.jpa.micronaut;

import com.forwardmeasure.jpa.identity.Actor;
import com.forwardmeasure.jpa.identity.IdentityType;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface MicronautActorRepository
        extends CrudRepository<Actor, Long> {

    Optional<Actor> findByUuid(UUID uuid);

    Optional<Actor> findByIdentityProviderAndSubjectIdentifier(
            String identityProvider, String subjectIdentifier);

    List<Actor> findByEmail(String email);

    List<Actor> findByType(IdentityType type);

    boolean existsByIdentityProviderAndSubjectIdentifier(
            String identityProvider, String subjectIdentifier);
}
