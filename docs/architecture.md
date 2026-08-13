# Architecture

## Boundaries

ForwardMeasure JPA separates four concerns:

1. The portable model defines persistence semantics, standard-JPA
   repositories, and application-facing service contracts.
2. Tenancy converts a tenant UUID to a validated schema identifier and binds
   that identifier to one synchronous execution.
3. Framework adapters connect that scope to each framework's Hibernate
   multitenancy lifecycle.
4. Liquibase owns the physical schema contract, while the deploying
   application owns migration scheduling.

The portable modules do not start containers, create an application, open
transactions, infer a tenant from HTTP, or run migrations at startup.

Persistent classes live only in explicit `entity` packages. Authorization
resource models do not belong in this foundation: consumers may adapt an
entity's public UUID and ownership relationship into their own authorization
model without making persistence entities implement security interfaces.

## Service Boundary

Resources, message consumers, schedulers, and workflow processors depend on
domain service interfaces. They do not inject repositories or an
`EntityManager`. The reusable service bases delegate common persistence
operations and expose their repository only through a protected accessor so a
domain implementation can add explicit queries.

Standard-JPA repository bases remain public extension points because consuming
domains implement repositories for their own entities. They are infrastructure
APIs, not an application-layer dependency. Shared and consumer service
implementations use Jakarta Transaction semantics; the framework adapters
register the same repository and service classes with the host container.

## Identity and Ownership

`Actor` is a root identity. Making it an `AuditedEntity` would imply that an
actor is owned by or audited through another actor, creating recursive
bootstrap and deletion problems. It therefore extends `AbstractBaseEntity`
directly and has its own public UUID.

`OwnedEntity` is the base for business resources that require an accountable
owner. Its `owner` is a required lazy JPA relationship to `Actor`, enforced by
each concrete table's foreign key. The mapped superclass does not declare a
fixed foreign-key constraint name because each concrete table requires a
unique database constraint name.

Lifecycle metadata on `AuditedEntity` is initialized by JPA callbacks in UTC.
Business-event and acting-principal audit history belongs in an append-only
audit subsystem rather than recursive columns on every entity.

## Tenancy

Tenant schemas use the existing convention:

```text
t_<tenant UUID without hyphens>
```

Only `public` or this exact lowercase pattern can construct a `TenantSchema`.
The connection providers use JDBC `Connection.setSchema` and reset every
connection to `public` before returning it to the pool.

Frameworks sometimes request a tenant identifier while compiling repositories
at startup. Spring and Micronaut receive a deliberately invalid unbound
identifier for that bootstrap phase. It cannot pass `TenantSchema` validation,
so obtaining a real connection without an execution scope still fails closed.

`ThreadBoundTenantScope` is appropriate for synchronous JPA work. It must not
be propagated across threads with an open `EntityManager`; a new tenant scope
and framework transaction must be opened on the destination thread.

## Repository Contract

The standard-JPA repositories support:

- save, lookup, count, delete, flush, and detach;
- deterministic bounded pagination with nested-property sorting;
- composable Criteria specifications;
- UUID lookup and bulk lookup for audited entities;
- actor identity, email, and identity-type lookup; and
- owner lookup, projection, counts, and ownership existence checks by primary
  ID or UUID.

Transactions and `EntityManager` lifecycles remain framework-owned.
Repository streams are transaction-bound and must be consumed and closed by
infrastructure code within the caller's transaction. The service-layer
`streamAll` operation materializes the result before returning so it never
leaks a JDBC cursor beyond the service transaction.

Lombok generates entity accessors, constructors, and hierarchy-aware builders.
Hibernate's annotation processor generates the canonical JPA metamodel for
every persistent class. Standard-JPA repositories use those metamodel
attributes for fixed framework fields; only genuinely dynamic consumer input,
such as a requested sort path, remains string-addressed.

MapStruct and its Lombok binding are centrally versioned and configured in the
parent build. MapStruct is used by concrete mapper interfaces, not by entities.
This foundation deliberately owns no API DTOs, so it does not invent mappings;
consumer API modules define their DTO contracts and generated mappers.

## Transaction-Scoped Locks

`forwardmeasure-jpa-locking` provides a database-independent named mutex using
standard JPA `PESSIMISTIC_WRITE`. The application migration owns its finite
set of lock rows; runtime applications use `acquireLock` and do not mutate the
provisioned definitions.

Acquisition requires an already-active transaction. The Quarkus, Spring, and
Micronaut adapters enforce mandatory transaction propagation; therefore the
row lock remains held until the caller's complete business transaction commits
or rolls back. A missing row fails closed as a deployment/migration error.

The optional `forwardmeasure-jpa-async-task` module supplies transport-neutral
task persistence and lifecycle semantics: idempotency, progress, attempts,
retry eligibility, processing leases, cancellation, terminal results, and
expiry. It does not dispatch work or prescribe Kafka, HTTP, or another
transport. Applications that do not need this capability omit the module.

## Verification

The shared contract is run through:

- plain Hibernate ORM;
- Quarkus Hibernate ORM;
- Spring Boot Hibernate JPA; and
- Micronaut Hibernate JPA.

Every integration uses a real PostgreSQL Testcontainer and the same Liquibase
changelog. The suite also verifies optimistic locking, tenant isolation,
migration idempotency, and unscoped-access failure.

The same adapter suites also exercise service-layer CRUD/query behavior and
mandatory transaction-scoped lock acquisition. A dedicated two-connection
PostgreSQL test proves that a competing lock acquisition blocks until the
owning transaction completes.
