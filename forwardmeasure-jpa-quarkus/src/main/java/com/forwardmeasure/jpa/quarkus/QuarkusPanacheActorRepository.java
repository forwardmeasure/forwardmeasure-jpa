package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.Actor_;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
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
        return find(Actor_.UUID, uuid).firstResultOptional();
    }

    public Optional<Actor> findByIdentity(
            String identityProvider, String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        if (identityProvider == null) {
            return find(
                    Actor_.IDENTITY_PROVIDER + " is null and "
                            + Actor_.SUBJECT_IDENTIFIER + " = ?1",
                    subjectIdentifier).firstResultOptional();
        }
        return find(
                Actor_.IDENTITY_PROVIDER + " = ?1 and "
                        + Actor_.SUBJECT_IDENTIFIER + " = ?2",
                identityProvider,
                subjectIdentifier).firstResultOptional();
    }

    public List<Actor> findByEmail(String email) {
        Objects.requireNonNull(email, "email");
        return List.copyOf(list(Actor_.EMAIL, email));
    }

    public List<Actor> findByType(IdentityType type) {
        Objects.requireNonNull(type, "type");
        return List.copyOf(list(Actor_.TYPE, type));
    }

    public boolean existsByIdentity(
            String identityProvider, String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        if (identityProvider == null) {
            return count(
                    Actor_.IDENTITY_PROVIDER + " is null and "
                            + Actor_.SUBJECT_IDENTIFIER + " = ?1",
                    subjectIdentifier) > 0L;
        }
        return count(
                Actor_.IDENTITY_PROVIDER + " = ?1 and "
                        + Actor_.SUBJECT_IDENTIFIER + " = ?2",
                identityProvider,
                subjectIdentifier) > 0L;
    }
}
