# Intentional Data Fabric Deviations

The original Data Fabric persistence code is the behavioral reference, not a
source template. The following differences are deliberate.

## Standard JPA Instead of Panache

Repositories use JPA Criteria, canonical metamodel attributes, and JPA
identifier metadata. No Panache, Spring Data, or Micronaut Data domain
repository hierarchy is exposed. Each entity has one repository class shared
by Quarkus, Spring Boot, and Micronaut.

## Actor Is an Identity Root

`Actor` extends `AbstractBaseEntity`, not `AuditedEntity` or `OwnedEntity`.
This avoids a recursive dependency in which the identity needed to audit or
own a row must itself first be audited or owned. Existing non-null actor
timestamp columns remain compatible through database defaults.

## Transaction-Safe Query Results

Single-result repository queries use a bounded result list rather than a JDBC
result stream. Service `streamAll` materializes its data before returning so a
framework cannot close the cursor when the service transaction ends.

## Framework Adapters Register the Same Components

The adapters do not define Quarkus-, Spring-, or Micronaut-specific domain
repositories and services. Spring and Micronaut bind their transaction-scoped
`EntityManager` proxies to the shared repositories. Quarkus performs normal
`@PersistenceContext` injection.

Micronaut transaction AOP is generated at compilation time and cannot advise
a portable service that was compiled in another artifact. The Micronaut
adapter therefore uses one generic interface proxy to apply the shared
Jakarta Transaction metadata. This avoids per-domain Micronaut service
duplicates while preserving REQUIRED and MANDATORY propagation.

## Async Tasks Are Optional and Stricter

The async-task entity, repository, lifecycle service, and status projection
handler live in the optional `forwardmeasure-jpa-async-task` module. Lifecycle
transitions fail closed, updates acquire a pessimistic lock, retry eligibility
is explicit, and processing leases are durable. The module stores lifecycle
state but does not dispatch work or select a transport.

The first certified database is PostgreSQL. Async JSON fields currently use
Hibernate's JSON mapping and the migration uses PostgreSQL JSONB and partial
indexes. Those choices are explicit; the core, identity, tenancy, and locking
modules remain standard JPA.

## Authorization and DID Semantics Stay Above JPA

Entities do not implement an authorization marker and the persistence model
does not manufacture DIDs. Security and DID layers can derive resource
identifiers from stable entity UUIDs and actor/tenant context without coupling
the persistence foundation to one authorization system.
