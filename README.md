# ForwardMeasure JPA

ForwardMeasure JPA is the provider-neutral persistence foundation for
ForwardMeasure services. It provides:

- portable base, audited, and actor-owned JPA entity models;
- explicit, fail-closed schema-per-tenant execution scopes;
- stable, application-owned Liquibase changelog fragments;
- standard JPA repositories plus Quarkus, Spring Data, and Micronaut Data
  integrations;
- reusable Testcontainers infrastructure; and
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

## Entity Model

- `AbstractBaseEntity` supplies optimistic locking only. It deliberately does
  not impose unsafe generic entity equality.
- `AuditedEntity` supplies an immutable public UUID and lifecycle timestamps.
- `Actor` is an identity root extending `AbstractBaseEntity`; it is neither
  audited nor owned.
- `OwnedEntity` extends `AuditedEntity` and carries a required, typed
  `ManyToOne<Actor>` relationship. Owner UUID stand-ins are not used.

Actor identities are unique by identity provider and subject identifier.
DIDs remain an optional higher-level identity integration and are not embedded
in this persistence foundation.

## Modules

| Module | Responsibility |
| --- | --- |
| `forwardmeasure-jpa-bom` | Consumer dependency management |
| `forwardmeasure-jpa-core` | Provider-neutral entity and repository contracts |
| `forwardmeasure-jpa-identity` | Actor identity and owned entities |
| `forwardmeasure-jpa-tenancy` | Tenant identifiers and schema scope |
| `forwardmeasure-jpa-liquibase` | Foundational database changelogs |
| `forwardmeasure-jpa-testcontainers` | Reusable real-database test fixtures |
| `forwardmeasure-jpa-contract-tests` | Adapter compatibility contract |
| `forwardmeasure-jpa-quarkus` | Quarkus Hibernate ORM/Panache adapter |
| `forwardmeasure-jpa-spring` | Spring Data JPA adapter |
| `forwardmeasure-jpa-micronaut` | Micronaut Data JPA adapter |

No framework dependency is exposed by `forwardmeasure-jpa-core`,
`forwardmeasure-jpa-identity`, or `forwardmeasure-jpa-tenancy`.

## Migrations

`forwardmeasure-jpa-liquibase` packages changelogs but never runs migrations
automatically. A deployment has exactly one migration owner. That owner
enumerates tenants and invokes `TenantSchemaMigrator` before application
workloads use the affected schema.

The historical Data Fabric changelog logical path, changeset IDs, and authors
are retained so an existing database does not replay foundational changesets
when it adopts this library. See [migration ownership](docs/migrations.md).

## Framework Notes

- Quarkus uses its native schema multitenancy SPIs. Applications configure
  `quarkus.hibernate-orm.multitenant=SCHEMA` and include their entity packages.
- Spring Boot auto-configuration installs the tenant resolver, connection
  provider, scope, and portable actor repository. Spring Data repository
  interfaces remain available for native use.
- Micronaut installs the equivalent services and supplies introspection
  metadata for the shared `Actor`. Consumer entities must be compiled with
  Micronaut introspection metadata, as shown in
  [framework integration](docs/framework-integration.md).

For design boundaries and guarantees, see [architecture](docs/architecture.md).
