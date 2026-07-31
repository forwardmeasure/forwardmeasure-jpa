package com.forwardmeasure.jpa.identity.repository;

import com.forwardmeasure.jpa.core.repository.JpaEntityRepository;
import com.forwardmeasure.jpa.identity.Actor;
import com.forwardmeasure.jpa.identity.IdentityType;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class JpaActorRepository
        extends JpaEntityRepository<Actor, Long>
        implements ActorRepository {

    public JpaActorRepository(EntityManager entityManager) {
        super(Actor.class, entityManager);
    }

    @Override
    public Optional<Actor> findByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return entityManager()
                .createQuery(
                        "select actor from Actor actor where actor.uuid = :uuid",
                        Actor.class)
                .setParameter("uuid", uuid)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<Actor> findByIdentity(
            String identityProvider, String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        return identityQuery(identityProvider, subjectIdentifier)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Actor> findByEmail(String email) {
        Objects.requireNonNull(email, "email");
        return List.copyOf(entityManager()
                .createQuery(
                        "select actor from Actor actor where actor.email = :email",
                        Actor.class)
                .setParameter("email", email)
                .getResultList());
    }

    @Override
    public List<Actor> findByType(IdentityType type) {
        Objects.requireNonNull(type, "type");
        return List.copyOf(entityManager()
                .createQuery(
                        "select actor from Actor actor where actor.type = :type",
                        Actor.class)
                .setParameter("type", type)
                .getResultList());
    }

    @Override
    public boolean existsByIdentity(
            String identityProvider, String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        String providerPredicate = identityProvider == null
                ? "actor.identityProvider is null"
                : "actor.identityProvider = :identityProvider";
        var query = entityManager()
                .createQuery(
                        "select count(actor) from Actor actor where "
                                + providerPredicate
                                + " and actor.subjectIdentifier = :subjectIdentifier",
                        Long.class)
                .setParameter("subjectIdentifier", subjectIdentifier);
        if (identityProvider != null) {
            query.setParameter("identityProvider", identityProvider);
        }
        return query.getSingleResult() > 0L;
    }

    private jakarta.persistence.TypedQuery<Actor> identityQuery(
            String identityProvider, String subjectIdentifier) {
        String providerPredicate = identityProvider == null
                ? "actor.identityProvider is null"
                : "actor.identityProvider = :identityProvider";
        var query = entityManager()
                .createQuery(
                        "select actor from Actor actor where "
                                + providerPredicate
                                + " and actor.subjectIdentifier = :subjectIdentifier",
                        Actor.class)
                .setParameter("subjectIdentifier", subjectIdentifier);
        if (identityProvider != null) {
            query.setParameter("identityProvider", identityProvider);
        }
        return query;
    }
}
