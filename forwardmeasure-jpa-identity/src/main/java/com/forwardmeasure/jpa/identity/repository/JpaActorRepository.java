package com.forwardmeasure.jpa.identity.repository;

import com.forwardmeasure.jpa.core.repository.JpaEntityRepository;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.Actor_;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
        CriteriaBuilder builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<Actor> query = builder.createQuery(Actor.class);
        Root<Actor> actor = query.from(Actor.class);
        query.select(actor).where(builder.equal(actor.get(Actor_.uuid), uuid));
        return entityManager().createQuery(query)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<Actor> findByIdentity(
            String identityProvider, String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        CriteriaBuilder builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<Actor> query = builder.createQuery(Actor.class);
        Root<Actor> actor = query.from(Actor.class);
        query.select(actor).where(identityPredicate(
                builder,
                actor,
                identityProvider,
                subjectIdentifier));
        return entityManager().createQuery(query)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Actor> findByEmail(String email) {
        Objects.requireNonNull(email, "email");
        CriteriaBuilder builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<Actor> query = builder.createQuery(Actor.class);
        Root<Actor> actor = query.from(Actor.class);
        query.select(actor).where(builder.equal(actor.get(Actor_.email), email));
        return List.copyOf(entityManager().createQuery(query)
                .getResultList());
    }

    @Override
    public List<Actor> findByType(IdentityType type) {
        Objects.requireNonNull(type, "type");
        CriteriaBuilder builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<Actor> query = builder.createQuery(Actor.class);
        Root<Actor> actor = query.from(Actor.class);
        query.select(actor).where(builder.equal(actor.get(Actor_.type), type));
        return List.copyOf(entityManager().createQuery(query)
                .getResultList());
    }

    @Override
    public boolean existsByIdentity(
            String identityProvider, String subjectIdentifier) {
        Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
        CriteriaBuilder builder = entityManager().getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Actor> actor = query.from(Actor.class);
        query.select(builder.count(actor)).where(identityPredicate(
                builder,
                actor,
                identityProvider,
                subjectIdentifier));
        return entityManager().createQuery(query).getSingleResult() > 0L;
    }

    private Predicate identityPredicate(
            CriteriaBuilder builder,
            Root<Actor> actor,
            String identityProvider,
            String subjectIdentifier) {
        Predicate provider = identityProvider == null
                ? builder.isNull(actor.get(Actor_.identityProvider))
                : builder.equal(
                        actor.get(Actor_.identityProvider),
                        identityProvider);
        return builder.and(
                provider,
                builder.equal(
                        actor.get(Actor_.subjectIdentifier),
                        subjectIdentifier));
    }
}
