# Architecture

## Boundaries

ForwardMeasure JPA separates four concerns:

1. The portable model defines persistence semantics and standard-JPA
   repositories.
2. Tenancy converts a tenant UUID to a validated schema identifier and binds
   that identifier to one synchronous execution.
3. Framework adapters connect that scope to each framework's Hibernate
   multitenancy lifecycle.
4. Liquibase owns the physical schema contract, while the deploying
   application owns migration scheduling.

The portable modules do not start containers, create an application, open
transactions, infer a tenant from HTTP, or run migrations at startup.

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

## Verification

The shared contract is run through:

- plain Hibernate ORM;
- Quarkus Hibernate ORM;
- Spring Boot and Spring Data JPA; and
- Micronaut Hibernate JPA and Micronaut Data.

Every integration uses a real PostgreSQL Testcontainer and the same Liquibase
changelog. The suite also verifies optimistic locking, tenant isolation,
migration idempotency, and unscoped-access failure.
