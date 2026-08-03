package com.forwardmeasure.jpa.spring;

import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringActorRepository extends JpaRepository<Actor, Long> {

    Optional<Actor> findByUuid(UUID uuid);

    Optional<Actor> findByIdentityProviderAndSubjectIdentifier(
            String identityProvider, String subjectIdentifier);

    List<Actor> findByEmail(String email);

    List<Actor> findByType(IdentityType type);

    boolean existsByIdentityProviderAndSubjectIdentifier(
            String identityProvider, String subjectIdentifier);
}
