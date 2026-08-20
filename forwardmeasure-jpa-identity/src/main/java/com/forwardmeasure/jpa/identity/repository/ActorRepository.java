package com.forwardmeasure.jpa.identity.repository;

import com.forwardmeasure.jpa.core.repository.AbstractBaseRepository;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.Actor_;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import jakarta.inject.Singleton;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Standard-JPA repository for actor identities. */
@Singleton
public class ActorRepository extends AbstractBaseRepository<Actor, Long> {

  public Optional<Actor> findByUuid(UUID uuid) {
    Objects.requireNonNull(uuid, "uuid");
    CriteriaBuilder builder = criteriaBuilder();
    CriteriaQuery<Actor> query = builder.createQuery(Actor.class);
    Root<Actor> actor = query.from(Actor.class);
    query.select(actor).where(builder.equal(actor.get(Actor_.uuid), uuid));
    return entityManager().createQuery(query).setMaxResults(1).getResultList().stream().findFirst();
  }

  public Optional<Actor> findByIdentity(String identityProvider, String subjectIdentifier) {
    Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
    CriteriaBuilder builder = criteriaBuilder();
    CriteriaQuery<Actor> query = builder.createQuery(Actor.class);
    Root<Actor> actor = query.from(Actor.class);
    query
        .select(actor)
        .where(identityPredicate(builder, actor, identityProvider, subjectIdentifier));
    return entityManager().createQuery(query).setMaxResults(1).getResultList().stream().findFirst();
  }

  public List<Actor> findByEmail(String email) {
    return findBy(Actor_.email, Objects.requireNonNull(email, "email"));
  }

  public List<Actor> findByType(IdentityType type) {
    return findBy(Actor_.type, Objects.requireNonNull(type, "type"));
  }

  public boolean existsByIdentity(String identityProvider, String subjectIdentifier) {
    Objects.requireNonNull(subjectIdentifier, "subjectIdentifier");
    CriteriaBuilder builder = criteriaBuilder();
    CriteriaQuery<Long> query = builder.createQuery(Long.class);
    Root<Actor> actor = query.from(Actor.class);
    query
        .select(builder.count(actor))
        .where(identityPredicate(builder, actor, identityProvider, subjectIdentifier));
    return entityManager().createQuery(query).getSingleResult() > 0L;
  }

  private <V> List<Actor> findBy(
      jakarta.persistence.metamodel.SingularAttribute<Actor, V> attribute, V value) {
    CriteriaBuilder builder = criteriaBuilder();
    CriteriaQuery<Actor> query = builder.createQuery(Actor.class);
    Root<Actor> actor = query.from(Actor.class);
    query.select(actor).where(builder.equal(actor.get(attribute), value));
    return List.copyOf(entityManager().createQuery(query).getResultList());
  }

  private static Predicate identityPredicate(
      CriteriaBuilder builder,
      Root<Actor> actor,
      String identityProvider,
      String subjectIdentifier) {
    Predicate provider =
        identityProvider == null
            ? builder.isNull(actor.get(Actor_.identityProvider))
            : builder.equal(actor.get(Actor_.identityProvider), identityProvider);
    return builder.and(
        provider, builder.equal(actor.get(Actor_.subjectIdentifier), subjectIdentifier));
  }
}
