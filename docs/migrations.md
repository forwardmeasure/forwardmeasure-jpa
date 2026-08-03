# Migration Ownership

## Rule

Exactly one deployment component owns database migration. Runtime services
must not race one another by automatically applying the same changelog.

`forwardmeasure-jpa-liquibase` is a library. It supplies:

- `db/changelog/forwardmeasure-jpa.xml`;
- the foundational actor schema; and
- `TenantSchemaMigrator` for applying a selected changelog to one validated
tenant schema.

`TenantSchemaMigrator` is deliberately a thin adapter over the independent
`forwardmeasure-database-migrations` API and Liquibase provider. The generic
project owns connection acquisition, target restoration, validation, status,
contexts, labels, parameters and provider execution. This JPA project owns only
its stable changelog fragments and the conversion from `TenantSchema` to an
explicit database target.

`forwardmeasure-jpa-locking` independently packages
`db/changelog/forwardmeasure-jpa-locking.xml`. Consumers include it only when
they use named transaction-scoped locks. Application changelogs own the lock
row seed data because lock names are domain contracts, not foundation data.

The platform migration application remains responsible for discovering
tenants, ordering platform and application changelogs, recording operational
status, and handling retry policy.

## Existing Data Fabric Databases

The foundational changelog intentionally retains:

- logical path `db/changelog/data-fabric-core-changelog.xml`;
- changeset IDs `core-001-create-sequences`, `core-010-create-actor`, and
  `core-011-actor-sequence-ownership`; and
- the original author.

Those three fields form Liquibase's changeset identity. Preserving them allows
an existing tenant schema to adopt the new artifact without replaying the
actor table creation.

The additive ForwardMeasure changeset adds database defaults to the legacy
actor timestamp columns. The corrected `Actor` model is not an
`AuditedEntity`, while the existing columns remain non-null for backward
compatibility. A PostgreSQL partial unique index also closes the legacy
nullable-provider loophole: two actors cannot share the same subject when
both identity providers are null.

## New Consumer Tables

A consumer owns its concrete entity tables and changelogs. A table extending
`OwnedEntity` must include:

- its primary key and sequence or other chosen ID generator;
- `version`, `uuid`, `created_at`, and `updated_at`;
- a non-null `owner_id`;
- a uniquely named foreign key from `owner_id` to `actor.id`; and
- a unique constraint on `uuid`.

The consumer root changelog should include
`db/changelog/forwardmeasure-jpa.xml` before its own changesets.
