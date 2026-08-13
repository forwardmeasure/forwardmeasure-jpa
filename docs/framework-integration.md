# Framework Integration

## Quarkus

Add `forwardmeasure-jpa-quarkus`, the PostgreSQL JDBC extension, and the
identity/model modules. Configure the application:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.hibernate-orm.multitenant=SCHEMA
quarkus.hibernate-orm.schema-management.strategy=none
quarkus.hibernate-orm.mapping.format.global=ignore
quarkus.hibernate-orm.packages=com.forwardmeasure.jpa.identity.entity,com.forwardmeasure.jpa.locking.entity,com.forwardmeasure.jpa.asynctask.entity,com.forwardmeasure.jpa.asynctask.converter,com.example.domain.entity
```

The format setting explicitly permits Hibernate JSON columns to use the
application's standard mapper when the application has not installed custom
JSON serialization behavior. An application with customized serialization
must instead provide the Quarkus persistence-unit `FormatMapper` described by
Quarkus; it must not silently reuse REST-specific customization for stored
data.

Liquibase remains deployment-owned. Bind `TenantScope` before beginning the
Quarkus transaction.

Application repositories extend `AbstractBaseRepository`,
`AbstractAuditedEntityRepository`, or `AbstractOwnedEntityRepository` and are
standard-JPA classes shared across hosts. Application code injects services,
not repositories. Shared services use `jakarta.transaction.Transactional`;
`SystemLockService.acquireLock` requires an existing transaction.

## Spring Boot

Adding `forwardmeasure-jpa-spring` activates
`ForwardMeasureJpaAutoConfiguration`. It supplies defaults only when the
application has not supplied its own tenant scope, resolver, connection
provider, shared repository, or service.

Scan shared and application entities and disable ORM schema generation:

```properties
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
```

The auto-configuration exposes the same `ActorRepository`, `ActorService`,
`SystemLockRepository`, `SystemLockService`, and optional async-task beans used
by the other hosts. There are no Spring Data domain-repository variants.
Controllers and messaging endpoints depend on domain services. Lock
acquisition uses mandatory Jakarta Transaction propagation and must occur
within the business transaction that the lock protects.

## Micronaut

Adding `forwardmeasure-jpa-micronaut` installs the tenant scope, Hibernate
services, and repository defaults. Micronaut performs entity discovery at
compile time. The adapter generates metadata for the shared identity model;
the consuming application generates metadata for its own JPA entities:

```java
@Introspected(
    packages = "com.example.domain",
    includedAnnotations = Entity.class)
final class ApplicationEntityIntrospection {
}
```

Configure entity scanning and disable schema generation:

```yaml
jpa:
  default:
    entity-scan:
      packages:
        - com.forwardmeasure.jpa.identity.entity
        - com.forwardmeasure.jpa.locking.entity
        - com.forwardmeasure.jpa.asynctask.entity
        - com.example.domain.entity
    properties:
      hibernate:
        hbm2ddl:
          auto: none
```

Micronaut registers the same common standard-JPA repositories and Jakarta
Transaction services. There are no Micronaut Data domain-repository variants.
Micronaut generates AOP at compile time and therefore cannot add transaction
interception to a precompiled portable service returned by a factory. The
adapter applies the Jakarta Transaction metadata through one generic service
proxy; it does not introduce Micronaut-specific domain implementations. Native
proxy metadata for the shared service interfaces is packaged with the adapter.
Direct repository work requires an active transaction; application entry
points normally use the service boundary. Lock acquisition requires an
existing transaction.

## Named Locks

An application that uses `SystemLockService` includes this changelog from its
deployment-owned migration root:

```xml
<include file="db/changelog/forwardmeasure-jpa-locking.xml" />
```

It then owns explicit, reviewable changesets that seed each supported
`system_lock.lock_name`. Runtime code never creates an absent lock implicitly.
