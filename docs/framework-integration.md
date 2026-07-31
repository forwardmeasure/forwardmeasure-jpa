# Framework Integration

## Quarkus

Add `forwardmeasure-jpa-quarkus`, the PostgreSQL JDBC extension, and the
identity/model modules. Configure the application:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.hibernate-orm.multitenant=SCHEMA
quarkus.hibernate-orm.schema-management.strategy=none
quarkus.hibernate-orm.packages=com.forwardmeasure.jpa.identity,com.example.domain
```

Liquibase remains deployment-owned. Bind `TenantScope` before beginning the
Quarkus transaction.

Applications may either subclass `QuarkusOwnedEntityRepository` for the
provider-neutral repository contract or implement `QuarkusOwnedRepository`
for a Panache-native repository. `QuarkusActorRepository` and
`QuarkusPanacheActorRepository` provide the corresponding actor alternatives.
The portable and Panache APIs are intentionally separate because their
`findById` return types are incompatible.

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

Spring Data applications can use `SpringActorRepository`,
`SpringAuditedRepository`, and `SpringOwnedRepository`. Applications that
prefer the provider-neutral API can subclass `SpringOwnedEntityRepository`.

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
        - com.forwardmeasure.jpa.identity
        - com.example.domain
    properties:
      hibernate:
        hbm2ddl:
          auto: none
```

Micronaut Data applications can use `MicronautActorRepository`,
`MicronautAuditedRepository`, and `MicronautOwnedRepository`. The
provider-neutral alternative is `MicronautOwnedEntityRepository`.
