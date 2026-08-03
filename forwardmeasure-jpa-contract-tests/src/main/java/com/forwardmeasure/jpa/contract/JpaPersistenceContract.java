package com.forwardmeasure.jpa.contract;

import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity;
import com.forwardmeasure.jpa.core.query.JpaSpecification;
import com.forwardmeasure.jpa.core.query.PageRequest;
import com.forwardmeasure.jpa.core.query.SortDirection;
import com.forwardmeasure.jpa.core.query.SortOrder;
import com.forwardmeasure.jpa.identity.entity.Actor;
import com.forwardmeasure.jpa.identity.entity.Actor_;
import com.forwardmeasure.jpa.identity.entity.IdentityType;
import com.forwardmeasure.jpa.identity.entity.OwnedEntity_;
import com.forwardmeasure.jpa.identity.repository.JpaActorRepository;
import com.forwardmeasure.jpa.identity.repository.JpaOwnedEntityRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;

/**
 * Shared persistence assertions executed by every supported adapter against a
 * real PostgreSQL database. The caller must provide an active transaction.
 */
public final class JpaPersistenceContract {

    private JpaPersistenceContract() {
    }

    public static ContractResult verify(EntityManager entityManager) {
        Objects.requireNonNull(entityManager, "entityManager");
        JpaActorRepository actors = new JpaActorRepository(entityManager);
        JpaOwnedEntityRepository<ContractOwnedEntity, Long> owned =
                new JpaOwnedEntityRepository<>(
                        ContractOwnedEntity.class, entityManager);

        Actor actor = new Actor();
        actor.setSubjectIdentifier("contract-user");
        actor.setIdentityProvider("contract-idp");
        actor.setType(IdentityType.HUMAN);
        actor.setEmail("contract@example.test");
        actors.save(actor);

        ContractOwnedEntity entity = new ContractOwnedEntity();
        entity.setName("first");
        entity.setOwner(actor);
        owned.save(entity);
        owned.flush();

        require(actor.getId() != null, "Actor id was not generated");
        require(actor.getUuid() != null, "Actor UUID was not generated");
        require(entity.getId() != null, "Owned entity id was not generated");
        require(entity.getUuid() != null, "Owned entity UUID was not generated");
        require(entity.getCreatedAt() != null, "createdAt was not populated");
        require(entity.getUpdatedAt() != null, "updatedAt was not populated");
        require(entity.getVersion() != null, "version was not populated");
        require(owned.findByUuid(entity.getUuid()).isPresent(),
                "UUID lookup failed");
        require(owned.existsByUuid(entity.getUuid()),
                "UUID existence lookup failed");
        require(owned.existsByIdAndOwnerId(entity.getId(), actor.getId()),
                "Owner-scoped id lookup failed");
        require(owned.existsByUuidAndOwnerId(
                        entity.getUuid(), actor.getId()),
                "Owner-scoped UUID lookup failed");
        require(owned.existsByIdAndOwnerSubjectIdentifier(
                        entity.getId(), "contract-user"),
                "Owner-subject id lookup failed");
        require(owned.existsByUuidAndOwnerSubjectIdentifier(
                        entity.getUuid(), "contract-user"),
                "Owner-subject UUID lookup failed");
        require(owned.findOwnerSubjectIdentifierById(entity.getId())
                        .filter("contract-user"::equals)
                        .isPresent(),
                "Owner subject projection by id failed");
        require(owned.findOwnerSubjectIdentifierByUuid(entity.getUuid())
                        .filter("contract-user"::equals)
                        .isPresent(),
                "Owner subject projection by UUID failed");
        require(owned.countByOwnerId(actor.getId()) == 1L,
                "Owner count failed");
        require(owned.findByOwnerSubjectIdentifier("contract-user").size() == 1,
                "Owner-subject lookup failed");
        require(actors.findByIdentity(
                        "contract-idp", "contract-user").isPresent(),
                "Actor identity lookup failed");
        require(actors.existsByIdentity(
                        "contract-idp", "contract-user"),
                "Actor identity existence lookup failed");
        require(actors.findByEmail("contract@example.test").size() == 1,
                "Actor email lookup failed");
        require(actors.findByType(IdentityType.HUMAN).size() == 1,
                "Actor type lookup failed");

        var page = owned.findAll(
                new PageRequest(
                        0,
                        10,
                        List.of(new SortOrder(
                                OwnedEntity_.OWNER + "."
                                        + Actor_.SUBJECT_IDENTIFIER,
                                SortDirection.ASCENDING))),
                (JpaSpecification<ContractOwnedEntity>)
                        (root, query, builder) ->
                                builder.equal(root.get("name"), "first"));
        require(page.totalItems() == 1L && page.items().size() == 1,
                "Specification paging failed");

        var originalUpdatedAt = entity.getUpdatedAt();
        entity.setName("updated");
        owned.flush();
        require(!entity.getUpdatedAt().isBefore(originalUpdatedAt),
                "updatedAt moved backwards");

        List<ContractOwnedEntity> byUuid =
                owned.findByUuids(List.of(entity.getUuid()));
        require(byUuid.size() == 1, "Bulk UUID lookup failed");

        return new ContractResult(
                actor.getId(),
                actor.getUuid(),
                entity.getId(),
                entity.getUuid(),
                entity.getVersion());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
