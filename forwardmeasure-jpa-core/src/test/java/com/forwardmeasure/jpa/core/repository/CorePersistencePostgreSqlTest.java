package com.forwardmeasure.jpa.core.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.core.query.PageRequest;
import com.forwardmeasure.jpa.core.query.SortDirection;
import com.forwardmeasure.jpa.core.query.SortOrder;
import com.forwardmeasure.jpa.core.support.CoreJpaFixture;
import com.forwardmeasure.jpa.core.support.CoreTestCategory;
import com.forwardmeasure.jpa.core.support.CoreTestCategory_;
import com.forwardmeasure.jpa.core.support.CoreTestEntity;
import com.forwardmeasure.jpa.core.support.CoreTestEntityRepository;
import com.forwardmeasure.jpa.core.support.CoreTestEntity_;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@WithPostgreSqlContainer(databaseName = "jpa_core_detailed_contract")
class CorePersistencePostgreSqlTest {

    @Test
    void persistsEntityAndAssignsGeneratedNonIdNamedIdentifier(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            CoreTestEntity created = fixture.transaction(context -> {
                CoreTestEntity entity = entity("one");
                context.repository().persist(entity);
                assertTrue(context.repository().isPersistent(entity));
                return entity;
            });

            assertNotNull(created.getDatabaseKey());
            assertEquals(created.getDatabaseKey(), created.getId());
            assertEquals(CoreTestEntity.class,
                    new CoreTestEntityRepository().entityClass());
        }
    }

    @Test
    void persistsAndFlushesEntityImmediately(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            Long id = fixture.transaction(context -> {
                CoreTestEntity entity = entity("flushed");
                context.repository().persistAndFlush(entity);
                assertNotNull(entity.getVersion());
                return entity.getId();
            });

            assertEquals("flushed", fixture.transaction(context ->
                    context.repository().findById(id).getName()));
        }
    }

    @Test
    void persistsIterableAndVarargsCollections(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            fixture.transaction(context -> {
                context.repository().persist(List.of(
                        entity("iterable-1"),
                        entity("iterable-2")));
                context.repository().persist(
                        entity("varargs-1"),
                        entity("varargs-2"),
                        entity("varargs-3"));
                context.repository().flush();
                assertEquals(5L, context.repository().count());
                return null;
            });
        }
    }

    @Test
    void findsEntityByIdentifierOptionalAndPessimisticLock(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            Long id = persist(fixture, "locked");

            fixture.transaction(context -> {
                assertEquals("locked", context.repository().findById(id)
                        .getName());
                assertTrue(context.repository().findByIdOptional(id)
                        .isPresent());
                assertEquals(
                        LockModeType.PESSIMISTIC_WRITE,
                        context.entityManager().getLockMode(
                                context.repository().findById(
                                        id,
                                        LockModeType.PESSIMISTIC_WRITE)));
                assertTrue(context.repository().findByIdOptional(
                                id,
                                LockModeType.PESSIMISTIC_WRITE)
                        .isPresent());
                assertTrue(context.repository().findByIdOptional(
                                Long.MAX_VALUE)
                        .isEmpty());
                return null;
            });
        }
    }

    @Test
    void detachesAndMergesEntity(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            Long id = persist(fixture, "before-merge");
            CoreTestEntity detached = fixture.transaction(context -> {
                CoreTestEntity entity = context.repository().findById(id);
                context.repository().detach(entity);
                assertFalse(context.repository().isPersistent(entity));
                return entity;
            });
            detached.setName("after-merge");

            CoreTestEntity merged = fixture.transaction(context -> {
                CoreTestEntity managed = context.repository().merge(detached);
                assertTrue(context.repository().isPersistent(managed));
                assertNotSame(detached, managed);
                return managed;
            });

            assertEquals("after-merge", merged.getName());
            assertEquals("after-merge", fixture.transaction(context ->
                    context.repository().findById(id).getName()));
        }
    }

    @Test
    void deletesManagedAndDetachedEntities(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            Long managedId = persist(fixture, "managed-delete");
            Long detachedId = persist(fixture, "detached-delete");

            fixture.transaction(context -> {
                context.repository().delete(
                        context.repository().findById(managedId));
                return null;
            });
            CoreTestEntity detached = fixture.transaction(context -> {
                CoreTestEntity entity = context.repository()
                        .findById(detachedId);
                context.repository().detach(entity);
                return entity;
            });
            fixture.transaction(context -> {
                context.repository().delete(detached);
                return null;
            });

            fixture.transaction(context -> {
                assertTrue(context.repository().findByIdOptional(managedId)
                        .isEmpty());
                assertTrue(context.repository().findByIdOptional(detachedId)
                        .isEmpty());
                return null;
            });
        }
    }

    @Test
    void deletesByIdentifierAndReportsMissingEntity(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            Long id = persist(fixture, "delete-by-id");
            fixture.transaction(context -> {
                assertTrue(context.repository().deleteById(id));
                assertFalse(context.repository().deleteById(Long.MAX_VALUE));
                return null;
            });
        }
    }

    @Test
    void deletesAllAndReturnsAffectedRowCount(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            fixture.transaction(context -> {
                context.repository().persist(
                        entity("delete-all-1"),
                        entity("delete-all-2"));
                context.repository().flush();
                assertEquals(2L, context.repository().deleteAll());
                assertEquals(0L, context.repository().count());
                return null;
            });
        }
    }

    @Test
    void listsAndStreamsAllEntities(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            fixture.transaction(context -> {
                context.repository().persist(
                        entity("list-1"),
                        entity("list-2"),
                        entity("list-3"));
                context.repository().flush();
                assertEquals(3, context.repository().listAll().size());
                try (var stream = context.repository().streamAll()) {
                    assertEquals(3L, stream.count());
                }
                return null;
            });
        }
    }

    @Test
    void pagesWithDefaultAndExplicitSorting(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            fixture.transaction(context -> {
                context.repository().persist(
                        entity("charlie"),
                        entity("alpha"),
                        entity("bravo"));
                context.repository().flush();

                var defaultPage = context.repository().page(
                        new PageRequest(0, 2, List.of()));
                assertEquals(3L, defaultPage.totalItems());
                assertEquals(2, defaultPage.items().size());
                assertTrue(defaultPage.items().get(0).getId()
                        < defaultPage.items().get(1).getId());

                var descending = context.repository().page(new PageRequest(
                        0,
                        3,
                        List.of(new SortOrder(
                                CoreTestEntity_.NAME,
                                SortDirection.DESCENDING))));
                assertEquals(
                        List.of("charlie", "bravo", "alpha"),
                        descending.items().stream()
                                .map(CoreTestEntity::getName)
                                .toList());

                var secondPage = context.repository().page(new PageRequest(
                        2,
                        2,
                        List.of(new SortOrder(
                                CoreTestEntity_.NAME,
                                SortDirection.ASCENDING))));
                assertEquals(List.of("charlie"), secondPage.items().stream()
                        .map(CoreTestEntity::getName)
                        .toList());
                assertEquals(3L, secondPage.totalItems());
                return null;
            });
        }
    }

    @Test
    void appliesSpecificationToPageDataAndCount(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            fixture.transaction(context -> {
                context.repository().persist(
                        entity("match"),
                        entity("match"),
                        entity("different"));
                context.repository().flush();
                var page = context.repository().page(
                        new PageRequest(0, 10, List.of()),
                        (root, query, builder) -> builder.equal(
                                root.get(CoreTestEntity_.name), "match"));
                assertEquals(2L, page.totalItems());
                assertEquals(2, page.items().size());
                return null;
            });
        }
    }

    @Test
    void sortsThroughValidatedAssociationPath(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            fixture.transaction(context -> {
                CoreTestCategory second = category("second");
                CoreTestCategory first = category("first");
                context.entityManager().persist(second);
                context.entityManager().persist(first);
                CoreTestEntity secondEntity = entity("entity-2");
                secondEntity.setCategory(second);
                CoreTestEntity firstEntity = entity("entity-1");
                firstEntity.setCategory(first);
                context.repository().persist(secondEntity, firstEntity);
                context.repository().flush();

                var page = context.repository().page(new PageRequest(
                        0,
                        10,
                        List.of(new SortOrder(
                                CoreTestEntity_.CATEGORY + "."
                                        + CoreTestCategory_.CODE,
                                SortDirection.ASCENDING))));
                assertEquals(
                        List.of("entity-1", "entity-2"),
                        page.items().stream()
                                .map(CoreTestEntity::getName)
                                .toList());
                return null;
            });
        }
    }

    @Test
    void rejectsInvalidPersistentPropertyPaths(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            fixture.transaction(context -> {
                assertThrows(IllegalArgumentException.class, () ->
                        context.repository().page(new PageRequest(
                                0,
                                10,
                                List.of(new SortOrder(
                                        "missing",
                                        SortDirection.ASCENDING)))));
                assertThrows(IllegalArgumentException.class, () ->
                        context.repository().page(new PageRequest(
                                0,
                                10,
                                List.of(new SortOrder(
                                        "category..code",
                                        SortDirection.ASCENDING)))));
                assertThrows(IllegalArgumentException.class, () ->
                        context.repository().page(new PageRequest(
                                0,
                                10,
                                List.of(new SortOrder(
                                        "name.value",
                                        SortDirection.ASCENDING)))));
                return null;
            });
        }
    }

    @Test
    void rejectsNullArgumentsAndUnboundPersistenceContexts(
            PostgreSqlTestContainer database) {
        CoreTestEntityRepository unbound = new CoreTestEntityRepository();
        assertThrows(IllegalStateException.class, unbound::count);

        try (var fixture = CoreJpaFixture.create(database)) {
            fixture.transaction(context -> {
                assertThrows(NullPointerException.class,
                        () -> context.repository().persist(
                                (CoreTestEntity) null));
                assertThrows(NullPointerException.class,
                        () -> context.repository().findById(null));
                assertThrows(NullPointerException.class,
                        () -> context.repository().page(null));
                assertThrows(NullPointerException.class,
                        () -> context.repository().detach(null));
                return null;
            });
        }
    }

    @Test
    void refusesPersistenceContextRebinding(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database);
                var first = fixture.sessions().openSession();
                var second = fixture.sessions().openSession()) {
            CoreTestEntityRepository repository =
                    new CoreTestEntityRepository();
            repository.bindPersistenceContext(first);
            repository.bindPersistenceContext(first);
            assertThrows(IllegalStateException.class,
                    () -> repository.bindPersistenceContext(second));
        }
    }

    @Test
    void initializesAndUpdatesAuditFieldsThroughJpaCallbacks(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            UUID explicitUuid = UUID.randomUUID();
            OffsetDateTime explicitCreated = OffsetDateTime.parse(
                    "2020-01-01T00:00:00Z");
            Long id = fixture.transaction(context -> {
                CoreTestEntity entity = entity("audited");
                entity.setUuid(explicitUuid);
                entity.setCreatedAt(explicitCreated);
                context.repository().persistAndFlush(entity);
                assertEquals(explicitUuid, entity.getUuid());
                assertEquals(explicitCreated, entity.getCreatedAt());
                assertNotNull(entity.getUpdatedAt());
                return entity.getId();
            });

            OffsetDateTime originalUpdated = fixture.transaction(context ->
                    context.repository().findById(id).getUpdatedAt());
            fixture.transaction(context -> {
                CoreTestEntity entity = context.repository().findById(id);
                entity.setName("audited-updated");
                context.repository().flush();
                assertEquals(explicitUuid, entity.getUuid());
                assertEquals(explicitCreated, entity.getCreatedAt());
                assertTrue(entity.getUpdatedAt().isAfter(originalUpdated));
                return null;
            });
        }
    }

    @Test
    void findsBulkChecksAndDeletesAuditedEntitiesByUuid(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            List<UUID> uuids = fixture.transaction(context -> {
                CoreTestEntity first = entity("uuid-1");
                CoreTestEntity second = entity("uuid-2");
                context.repository().persist(first, second);
                context.repository().flush();
                assertTrue(context.repository().findByUuid(first.getUuid())
                        .isPresent());
                assertTrue(context.repository().existsByUuid(first.getUuid()));
                assertEquals(2, context.repository().findByUuids(
                        List.of(first.getUuid(), second.getUuid())).size());
                assertTrue(context.repository().findByUuids(List.of())
                        .isEmpty());
                return List.of(first.getUuid(), second.getUuid());
            });

            fixture.transaction(context -> {
                assertTrue(context.repository().deleteByUuid(uuids.get(0)));
                assertFalse(context.repository().deleteByUuid(UUID.randomUUID()));
                assertFalse(context.repository().existsByUuid(uuids.get(0)));
                assertTrue(context.repository().existsByUuid(uuids.get(1)));
                return null;
            });
        }
    }

    @Test
    void rejectsStaleOptimisticUpdate(
            PostgreSqlTestContainer database) {
        try (var fixture = CoreJpaFixture.create(database)) {
            Long id = persist(fixture, "optimistic");
            try (var first = fixture.sessions().openSession();
                    var second = fixture.sessions().openSession()) {
                var firstTransaction = first.beginTransaction();
                var secondTransaction = second.beginTransaction();
                CoreTestEntity firstCopy = first.find(
                        CoreTestEntity.class, id);
                CoreTestEntity secondCopy = second.find(
                        CoreTestEntity.class, id);
                firstCopy.setName("first-writer");
                firstTransaction.commit();
                secondCopy.setName("stale-writer");
                assertThrows(
                        OptimisticLockException.class,
                        secondTransaction::commit);
            }
        }
    }

    private Long persist(CoreJpaFixture fixture, String name) {
        return fixture.transaction(context -> {
            CoreTestEntity entity = entity(name);
            context.repository().persistAndFlush(entity);
            return entity.getId();
        });
    }

    private CoreTestEntity entity(String name) {
        CoreTestEntity entity = new CoreTestEntity();
        entity.setName(name);
        return entity;
    }

    private CoreTestCategory category(String code) {
        CoreTestCategory category = new CoreTestCategory();
        category.setCode(code);
        return category;
    }
}
