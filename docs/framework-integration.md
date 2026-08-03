# Framework Integration

## Quarkus

Add `forwardmeasure-jpa-quarkus`, the PostgreSQL JDBC extension, and the
identity/model modules. Configure the application:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.hibernate-orm.multitenant=SCHEMA
quarkus.hibernate-orm.schema-management.strategy=none
quarkus.hibernate-orm.packages=com.forwardmeasure.jpa.identity.entity,com.forwardmeasure.jpa.locking.entity,com.example.domain.entity
```

Liquibase remains deployment-owned. Bind `TenantScope` before beginning the
Quarkus transaction.

Applications may either subclass `QuarkusOwnedEntityRepository` for the
provider-neutral repository contract or implement `QuarkusOwnedRepository`
for a Panache-native repository. `QuarkusActorRepository` and
`QuarkusPanacheActorRepository` provide the corresponding actor alternatives.
The portable and Panache repository APIs are intentionally separate because their
`findById` return types are incompatible.

Application code injects `ActorService` and `SystemLockService`, not those
repositories. `QuarkusActorService` supplies normal transactional propagation;
`QuarkusSystemLockService.acquire` requires an existing transaction. Concrete
domain services should use Quarkus `@Transactional` on the adapter class.

## Spring Boot

Adding `forwardmeasure-jpa-spring` activates
`ForwardMeasureJpaAutoConfiguration`. It supplies defaults only when the
application has not supplied its own tenant scope, resolver, connection
provider, or actor repository.

Scan shared and application entities and disable ORM schema generation:

```properties
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
```

The auto-configuration exposes portable `ActorService` and
`SystemLockService` beans. Spring Data persistence adapters can use
`SpringActorRepository`,
`SpringAuditedRepository`, and `SpringOwnedRepository`. Applications that
prefer the provider-neutral API can subclass `SpringOwnedEntityRepository`.
Controllers and messaging endpoints depend on domain services, while concrete
Spring service adapters use Spring `@Transactional`. Lock acquisition uses
mandatory propagation and must occur within the business transaction that the
lock protects.

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
        - com.example.domain.entity
    properties:
      hibernate:
        hbm2ddl:
          auto: none
```

Micronaut Data applications can use `MicronautActorRepository`,
`MicronautAuditedRepository`, and `MicronautOwnedRepository`. The
provider-neutral alternative is `MicronautOwnedEntityRepository`.

Portable `ActorService` and `SystemLockService` beans are available for
application injection. Concrete domain service adapters use Micronaut
`@Transactional`; lock acquisition requires an existing transaction.

## Named Locks

An application that uses `SystemLockService` includes this changelog from its
deployment-owned migration root:

```xml
<include file="db/changelog/forwardmeasure-jpa-locking.xml" />
```

It then owns explicit, reviewable changesets that seed each supported
`system_lock.lock_name`. Runtime code never creates an absent lock implicitly.
