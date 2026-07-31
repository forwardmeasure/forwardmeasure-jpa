package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.identity.Actor;
import com.forwardmeasure.jpa.identity.IdentityType;
import io.quarkus.arc.DefaultBean;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Panache-native repository for actor identities.
 *
 * <p>{@link QuarkusActorRepository} remains the provider-neutral alternative.
 */
@ApplicationScoped
@DefaultBean
public class QuarkusPanacheActorRepository
        implements PanacheRepositoryBase<Actor, Long> {

    public Optional<Actor> findByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return find("uuid", uuid).firstResultOptional();
    }

    public Optional<Actor> findByIdentity(
            String identityProvider, String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        if (identityProvider == null) {
            return find(
                    "identityProvider is null and subjectIdentifier = ?1",
                    subjectIdentifier).firstResultOptional();
        }
        return find(
                "identityProvider = ?1 and subjectIdentifier = ?2",
                identityProvider,
                subjectIdentifier).firstResultOptional();
    }

    public List<Actor> findByEmail(String email) {
        Objects.requireNonNull(email, "email");
        return List.copyOf(list("email", email));
    }

    public List<Actor> findByType(IdentityType type) {
        Objects.requireNonNull(type, "type");
        return List.copyOf(list("type", type));
    }

    public boolean existsByIdentity(
            String identityProvider, String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        if (identityProvider == null) {
            return count(
                    "identityProvider is null and subjectIdentifier = ?1",
                    subjectIdentifier) > 0L;
        }
        return count(
                "identityProvider = ?1 and subjectIdentifier = ?2",
                identityProvider,
                subjectIdentifier) > 0L;
    }
}
