package com.forwardmeasure.jpa.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.jpa.core.query.PageRequest;
import com.forwardmeasure.jpa.core.service.impl.AbstractBaseServiceImpl;
import com.forwardmeasure.jpa.core.support.CoreJpaFixture;
import com.forwardmeasure.jpa.core.support.CoreTestEntity;
import com.forwardmeasure.jpa.core.support.CoreTestEntity_;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@WithPostgreSqlContainer(databaseName = "jpa_core_service_contract")
class CoreServicePostgreSqlTest {

  @Test
  void servicePersistsFlushesFindsAndCountsEntities(PostgreSqlTestContainer database) {
    try (var fixture = CoreJpaFixture.create(database)) {
      Long id =
          fixture.transaction(
              context -> {
                CoreTestEntity entity = entity("service-create");
                context.service().persistAndFlush(entity);
                assertNotNull(entity.getId());
                assertTrue(context.service().isPersistent(entity));
                assertEquals(1L, context.service().count());
                return entity.getId();
              });

      fixture.transaction(
          context -> {
            assertEquals("service-create", context.service().findById(id).getName());
            assertTrue(context.service().findByIdOptional(id).isPresent());
            assertTrue(context.service().findByIdOptional(Long.MAX_VALUE).isEmpty());
            assertNotNull(context.service().findById(id, LockModeType.PESSIMISTIC_READ));
            assertTrue(
                context.service().findByIdOptional(id, LockModeType.PESSIMISTIC_READ).isPresent());
            return null;
          });
    }
  }

  @Test
  void servicePersistsIterableAndVarargsAndListsEntities(PostgreSqlTestContainer database) {
    try (var fixture = CoreJpaFixture.create(database)) {
      fixture.transaction(
          context -> {
            context
                .service()
                .persist(List.of(entity("service-iterable-1"), entity("service-iterable-2")));
            context.service().persist(entity("service-varargs-1"), entity("service-varargs-2"));
            context.service().flush();
            assertEquals(4, context.service().listAll().size());
            return null;
          });
    }
  }

  @Test
  void serviceReturnsMaterializedStreamAfterTransactionCloses(PostgreSqlTestContainer database) {
    try (var fixture = CoreJpaFixture.create(database)) {
      fixture.transaction(
          context -> {
            context.service().persist(entity("stream-1"), entity("stream-2"));
            return null;
          });

      var stream = fixture.transaction(context -> context.service().streamAll());
      try (stream) {
        assertEquals(2L, stream.count());
      }
    }
  }

  @Test
  void serviceMergesDetachesAndDeletesEntities(PostgreSqlTestContainer database) {
    try (var fixture = CoreJpaFixture.create(database)) {
      CoreTestEntity detached =
          fixture.transaction(
              context -> {
                CoreTestEntity entity = entity("service-detached");
                context.service().persistAndFlush(entity);
                context.service().detach(entity);
                assertFalse(context.service().isPersistent(entity));
                return entity;
              });
      detached.setName("service-merged");

      fixture.transaction(
          context -> {
            CoreTestEntity merged = context.service().merge(detached);
            context.service().flush();
            assertEquals("service-merged", merged.getName());
            context.service().delete(merged);
            return null;
          });

      fixture.transaction(
          context -> {
            assertFalse(context.service().findByIdOptional(detached.getId()).isPresent());
            return null;
          });
    }
  }

  @Test
  void servicePagesAndFiltersEntities(PostgreSqlTestContainer database) {
    try (var fixture = CoreJpaFixture.create(database)) {
      fixture.transaction(
          context -> {
            context
                .service()
                .persist(entity("service-match"), entity("service-match"), entity("service-other"));
            context.service().flush();
            assertEquals(
                3L, context.service().page(new PageRequest(0, 10, List.of())).totalItems());
            var filtered =
                context
                    .service()
                    .page(
                        new PageRequest(0, 10, List.of()),
                        (root, query, builder) ->
                            builder.equal(root.get(CoreTestEntity_.name), "service-match"));
            assertEquals(2L, filtered.totalItems());
            assertEquals(2, filtered.items().size());
            return null;
          });
    }
  }

  @Test
  void auditedServiceFindsChecksAndDeletesByUuid(PostgreSqlTestContainer database) {
    try (var fixture = CoreJpaFixture.create(database)) {
      List<UUID> uuids =
          fixture.transaction(
              context -> {
                CoreTestEntity first = entity("service-uuid-1");
                CoreTestEntity second = entity("service-uuid-2");
                context.service().persist(first, second);
                context.service().flush();
                assertTrue(context.service().findByUuid(first.getUuid()).isPresent());
                assertTrue(context.service().existsByUuid(first.getUuid()));
                assertEquals(
                    2,
                    context
                        .service()
                        .findByUuids(List.of(first.getUuid(), second.getUuid()))
                        .size());
                return List.of(first.getUuid(), second.getUuid());
              });

      fixture.transaction(
          context -> {
            assertTrue(context.service().deleteByUuid(uuids.get(0)));
            assertFalse(context.service().deleteByUuid(UUID.randomUUID()));
            assertFalse(context.service().existsByUuid(uuids.get(0)));
            return null;
          });
    }
  }

  @Test
  void serviceDeletesByIdentifierAndDeletesAll(PostgreSqlTestContainer database) {
    try (var fixture = CoreJpaFixture.create(database)) {
      fixture.transaction(
          context -> {
            CoreTestEntity first = entity("service-delete-id");
            CoreTestEntity second = entity("service-delete-all");
            context.service().persist(first, second);
            context.service().flush();
            assertTrue(context.service().deleteById(first.getId()));
            assertFalse(context.service().deleteById(Long.MAX_VALUE));
            assertEquals(1L, context.service().deleteAll());
            assertEquals(0L, context.service().count());
            return null;
          });
    }
  }

  @Test
  void serviceImplementationDeclaresTransactionalBoundary() {
    assertNotNull(AbstractBaseServiceImpl.class.getAnnotation(Transactional.class));
  }

  @Test
  void serviceRejectsNullInputs(PostgreSqlTestContainer database) {
    try (var fixture = CoreJpaFixture.create(database)) {
      fixture.transaction(
          context -> {
            assertThrows(
                NullPointerException.class, () -> context.service().persist((CoreTestEntity) null));
            assertThrows(NullPointerException.class, () -> context.service().findById(null));
            assertThrows(NullPointerException.class, () -> context.service().detach(null));
            return null;
          });
    }
  }

  private CoreTestEntity entity(String name) {
    CoreTestEntity entity = new CoreTestEntity();
    entity.setName(name);
    return entity;
  }
}
