# ForwardMeasure JPA

ForwardMeasure JPA is the provider-neutral persistence foundation for
ForwardMeasure services. It provides:

- portable base, audited, and actor-owned JPA entity models;
- explicit, fail-closed schema-per-tenant execution scopes;
- stable, application-owned Liquibase changelog fragments;
- portable application service contracts over standard JPA repositories;
- explicit entity packages with Lombok-backed models and generated canonical
  JPA metamodels;
- Quarkus, Spring Boot, and Micronaut integrations over the same standard-JPA
  repositories and Jakarta Transaction service implementations;
- transaction-scoped, database-independent named locks;
- optional durable asynchronous-task persistence and lifecycle services;
- integration with the reusable `forwardmeasure-testcontainers` foundation; and
- one shared persistence contract executed by every supported framework.

The project targets Java 25. PostgreSQL 18 is the first certified database.
The portable model and repository layer has no dependency on Quarkus, Spring,
Micronaut, Hibernate, or PostgreSQL-specific APIs.

## Build

```bash
mvn clean verify
```

The build uses real PostgreSQL containers. Docker or a compatible container
runtime must be available. There are no mocked persistence tests.

## Use

Import the BOM and select only the model and framework adapter required by the
application:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.forwardmeasure.jpa</groupId>
            <artifactId>forwardmeasure-jpa-bom</artifactId>
            <version>${forwardmeasure-jpa.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.forwardmeasure.jpa</groupId>
        <artifactId>forwardmeasure-jpa-identity</artifactId>
    </dependency>
    <dependency>
        <groupId>com.forwardmeasure.jpa</groupId>
        <artifactId>forwardmeasure-jpa-quarkus</artifactId>
    </dependency>
</dependencies>
```

An execution must bind a validated tenant schema before opening a transaction:

```java
try (TenantScope.Scope ignored = tenantScope.open(
        TenantSchema.forTenant(new TenantId(tenantUuid)))) {
    // Execute the framework-managed transaction here.
}
```

The scope is nestable, must be closed on its opening thread, and removes its
`ThreadLocal` when empty. An unscoped persistence operation cannot silently
fall back to `public`.

Application code depends on a portable service contract, not a repository or
an `EntityManager`. Domain services extend the matching reusable base and add
domain-specific operations explicitly:

```java
public interface EvidenceService
        extends OwnedEntityService<Evidence, Long> {

    Optional<Evidence> findByExternalReference(String externalReference);
}

public final class EvidenceServiceImpl
        extends OwnedEntityServiceImpl<
                Evidence,
                Long,
                EvidenceRepository>
        implements EvidenceService {

    public EvidenceServiceImpl(EvidenceRepository repository) {
        super(repository);
    }

    @Override
    public Optional<Evidence> findByExternalReference(
            String externalReference) {
        return repository().findByExternalReference(externalReference);
    }
}
```

The concrete service uses Jakarta Transaction semantics inherited from the
shared service implementation. HTTP resources, messaging consumers, and
workflow processors inject `EvidenceService`; only service implementations
inject repositories.

## Entity Model

- `AbstractBaseEntity` supplies optimistic locking and identifier-based
  equality for non-transient instances of the exact same entity class.
- `AuditedEntity` supplies an immutable public UUID and lifecycle timestamps.
- `Actor` is an identity root extending `AbstractBaseEntity`; it is neither
  audited nor owned.
- `OwnedEntity` extends `AuditedEntity` and carries a required, typed
  `ManyToOne<Actor>` relationship. Owner UUID stand-ins are not used.

Actor identities are unique by identity provider and subject identifier.
DIDs remain an optional higher-level identity integration and are not embedded
in this persistence foundation.

The JPA foundation also contains no authorization marker interface. Security
layers adapt stable entity UUIDs and ownership relationships to their own
resource model instead of coupling every persistent entity to authorization.

## Code Generation

Lombok, Hibernate's JPA metamodel processor, MapStruct, and the Lombok/MapStruct
binding are centrally versioned in the parent POM. Persistent classes use
Lombok; canonical metamodel classes such as `Actor_` and `OwnedEntity_` are
generated at compilation and used by the repository implementations.

MapStruct belongs on mapper interfaces, not entity classes. This repository
does not own API DTO contracts, so consumer API modules define their DTOs and
MapStruct mappers. The API artifact versions are also exported by the
ForwardMeasure JPA BOM; annotation-processor configuration remains owned by
the consuming build.

## Modules

| Module | Responsibility |
| --- | --- |
| `forwardmeasure-jpa-bom` | Consumer dependency management |
| `forwardmeasure-jpa-core` | Provider-neutral entities, repositories, and application services |
| `forwardmeasure-jpa-identity` | Actor identity, owned entities, and ownership services |
| `forwardmeasure-jpa-locking` | Transaction-scoped named database mutexes |
| `forwardmeasure-jpa-async-task` | Optional durable asynchronous-task lifecycle and persistence |
| `forwardmeasure-jpa-tenancy` | Tenant identifiers and schema scope |
| `forwardmeasure-jpa-liquibase` | Foundational database changelogs |
| `forwardmeasure-jpa-contract-tests` | Adapter compatibility contract |
| `forwardmeasure-jpa-quarkus` | Quarkus Hibernate ORM registration and schema tenancy |
| `forwardmeasure-jpa-spring` | Spring Boot registration and schema tenancy |
| `forwardmeasure-jpa-micronaut` | Micronaut Hibernate registration and schema tenancy |

No framework dependency is exposed by `forwardmeasure-jpa-core`,
`forwardmeasure-jpa-identity`, `forwardmeasure-jpa-locking`, or
`forwardmeasure-jpa-tenancy`.

Integration tests consume the external `forwardmeasure-testcontainers`
PostgreSQL and JUnit artifacts. This repository does not implement or package
its own container lifecycle.

## Migrations

`forwardmeasure-jpa-liquibase` packages changelogs but never runs migrations
automatically. It is a tenancy adapter over
`forwardmeasure-database-migrations-liquibase`; it does not own JDBC lifecycle
or Liquibase execution itself. A deployment has exactly one migration owner.
That owner enumerates tenants and invokes `TenantSchemaMigrator` before
application workloads use the affected schema. The migrator exposes validation,
status inspection, and idempotent migration results.

The historical Data Fabric changelog logical path, changeset IDs, and authors
are retained so an existing database does not replay foundational changesets
when it adopts this library. See [migration ownership](docs/migrations.md).

## Framework Notes

- Quarkus uses its native schema multitenancy SPIs. Applications configure
  `quarkus.hibernate-orm.multitenant=SCHEMA` and include their entity packages.
- Spring Boot auto-configuration installs the tenant resolver, connection
  provider, scope, and the shared repositories and services.
- Micronaut installs the equivalent services and supplies introspection
  metadata for the shared entities. Consumer entities must be compiled with
  Micronaut introspection metadata, as shown in
  [framework integration](docs/framework-integration.md).

For design boundaries and guarantees, see [architecture](docs/architecture.md)
and the documented [Data Fabric deviations](docs/data-fabric-deviations.md).
