# Migration Ownership

## Rule

Exactly one deployment component owns database migration. Runtime services
must not race one another by automatically applying the same changelog.

Every changelog is written as it should exist from scratch: one clean create
per table, with no patch changesets (no ALTERs, data moves or backfills layered
on earlier changesets). Constraint or sequence-ownership SQL that Liquibase OSS
cannot express as a tag lives inside the same create changeset.

`forwardmeasure-jpa-liquibase` is a library. It supplies:

- `db/changelog/forwardmeasure-jpa-identity.xml`, the tenant's identity schema;
- `TenantIdentityMigrator`, which creates and migrates the identity schema in a
  tenant database and grants roles access to it; and
- `TenantSchemaMigrator` for applying a product's changelog to one validated
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

## The Identity Schema

Each tenant database has exactly one actor table, `identity.actor`
(`IdentitySchema.NAME`), shared by every product in that tenant. An actor is
one person or service account with access to the tenant, identified by
`(identity_provider, subject_identifier)`; both are required.

- Actors are provisioned deliberately when tenant access is granted, and
  disabled (`disabled_at`), never deleted, when it is removed. Nothing creates
  an actor on first sight; an unknown or disabled actor is refused.
- Tenant provisioning runs `TenantIdentityMigrator.migrate()` before migrating
  any product's functional schema. It is idempotent and safe to run from every
  product's provisioning.
- Products' runtime roles get `TenantIdentityMigrator.grantRead(role)`: they
  read actors and nothing else in the identity schema. Only the tenant-access
  provisioning identity gets `grantProvisioning(role)`, which can create and
  disable actors but not delete them.
- No product changelog creates an actor table.

## New Consumer Tables

A consumer owns its concrete entity tables and changelogs in its own
functional schema. A table extending `OwnedEntity` must include:

- its primary key and sequence or other chosen ID generator;
- `version`, `uuid`, `created_at`, and `updated_at`;
- a non-null `owner_id`;
- a uniquely named foreign key from `owner_id` to `identity.actor(id)`
  (`referencedTableSchemaName="identity"`); and
- a unique constraint on `uuid`.

Any other column that records who did something (an audit or lifecycle
actor) is likewise a foreign key to `identity.actor(id)`.
