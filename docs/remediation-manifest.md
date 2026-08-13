# ForwardMeasure JPA Remediation Manifest

## Mandate

Repair `forwardmeasure-jpa` in place. The established Data Fabric JPA entity,
repository, service, and service-implementation structure is the behavioral
reference. The remediation must not redesign OKS, change downstream product
semantics, introduce a persistence SPI, or add framework-native repository
programming models.

## Non-negotiable implementation rules

1. JPA is the shared persistence technology.
2. Each domain entity has one standard-JPA repository class.
3. Services are interfaces with concrete implementations in `service.impl`.
4. Applications inject services; services inject repositories.
5. No service injects `EntityManager`.
6. Repository consumers do not supply an entity class or `EntityManager`.
7. Fixed persistent attributes use the generated JPA metamodel.
8. Identifier handling uses JPA metadata and never assumes an `id` property.
9. Quarkus, Spring, and Micronaut modules provide only registration,
   transactions, tenancy, lifecycle, and required framework metadata.
10. Framework-specific repository and service hierarchies are prohibited.
11. The async-task capability removed during extraction is restored as an
    optional module.
12. Verification uses real PostgreSQL Testcontainers and no mocks.
13. Versions remain `1.0.0`.
14. OKS and other repositories remain untouched until this reactor is green.

## Target packages

- `core.entity`
- `core.repository`
- `core.query`
- `core.service`
- `core.service.impl`
- `identity.entity`
- `identity.repository`
- `identity.service`
- `identity.service.impl`
- `locking.entity`
- `locking.repository`
- `locking.service`
- `locking.service.impl`
- `asynctask.entity`
- `asynctask.converter`
- `asynctask.repository`
- `asynctask.service`
- `asynctask.service.impl`

## Classes to eliminate

- `EntityRepository` / `JpaEntityRepository` duplication
- `AuditedEntityRepository` / `JpaAuditedEntityRepository` duplication
- `ActorRepository` / `JpaActorRepository` duplication
- `OwnedEntityRepository` / `JpaOwnedEntityRepository` duplication
- `SystemLockRepository` / `JpaSystemLockRepository` duplication
- `RepositoryActorService`
- `RepositorySystemLockService`
- all `Quarkus*Repository`, `Spring*Repository`, and `Micronaut*Repository`
  domain variants
- all `Quarkus*Service`, `Spring*Service`, and `Micronaut*Service` domain
  variants
- Quarkus Panache repository alternatives
- `beans.xml` once indexed dependency discovery is proven

## Completion gates

- clean and legacy-schema Liquibase execution;
- equality, persistence, pagination, sorting, specification, identifier,
  auditing, ownership, and tenant-isolation contracts;
- transaction-scoped locking with two real database connections;
- complete async-task lifecycle, retry, lease, progress, cancellation,
  idempotency, result, and expiry contracts;
- equivalent dependency injection and transaction behavior in Quarkus,
  Spring Boot, and Micronaut;
- one final clean `mvn verify` for the complete reactor;
- a documented list of intentional deviations from Data Fabric.

## Completion status

Completed on 2026-08-13.

- The complete 12-module `mvn clean verify` reactor passed with 35 tests,
  including real PostgreSQL contracts for plain JPA, Quarkus, Spring Boot,
  and Micronaut.
- A final Spring-focused reactor proved the transaction-proxy correction and
  completed without proxy warnings.
- The affected core module was then clean-compiled after adding the targeted
  generic-varargs warning suppression.
- `git diff --check` passes.
- No test uses mocks.
- No service injects `EntityManager`; persistence-context binding is confined
  to the shared repository base and framework registration adapters.
- No framework-specific domain repository or domain service variant remains.
