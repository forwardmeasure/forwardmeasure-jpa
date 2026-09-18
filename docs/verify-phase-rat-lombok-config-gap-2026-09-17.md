# `mvn verify`/`install` fails at the root: RAT license-check rejects `lombok.config`

Found while verifying the database-per-tenant registry work (`TenantDatabase`,
`FunctionalSchema`, `TenantRegistry`). Not caused by, or specific to, that
work - it reproduces on this repo's pre-existing, already-committed state and
is unrelated to any file this effort touched.

## Symptom

Any `mvn ... verify` or `mvn ... install` run from the repository root (with
`-am`, so the root aggregator pom itself enters the reactor) fails:

```
[ERROR] Unexpected count for UNAPPROVED, limit is [0,0].  Count: 1
[ERROR] Failed to execute goal org.apache.rat:apache-rat-plugin:0.18:check
(license-check) on project forwardmeasure-jpa-parent
```

`target/rat.txt` names the offending file:

```
Files with unapproved licenses
  /lombok.config
```

`mvn ... test` (and earlier phases) does not trigger this - the RAT check
is bound to the `verify` phase, one step after `test` in the default
lifecycle - so it went unnoticed by phase-scoped `compile`/`test-compile`/
`test` runs.

## Root cause

`/lombok.config` at this repo's root has been committed since `5efc245`
("Refactored to include code coverage") and carries no license header (it
can't - Lombok's config format has no comment syntax for one, similar to the
existing `inputExclude` carved out for
`values.schema.json` for the same reason). The Apache RAT plugin's
`inputExcludes` list, defined once in the shared `forwardmeasure-platform`
parent pom (`pom.xml:1079-1099`) and inherited here, has no entry for
`lombok.config`. `forwardmeasure-platform` was just bumped 1.0.0 -> 1.1.0 in
this repo's root `pom.xml` as part of this session's dependency-version work;
it's not yet confirmed whether the exclude was ever present at any prior
`forwardmeasure-platform` version or whether this gap is longstanding and
simply never previously exercised by a root-level `verify`/`install` in this
repo.

## Impact

Every module compiles and every test passes; this is purely a `verify`/
`install`-phase gate failure, and only when the build includes the root
aggregator pom. `mvn -pl <module> -am test` (this session's own build/test
commands throughout) is unaffected.

## Suggested fix (not applied here - out of scope for this task)

Add an `inputExclude` for `lombok.config` (or `/lombok.config`) to the RAT
plugin configuration in `forwardmeasure-platform`'s root `pom.xml`, the same
way `values.schema.json` is already excluded for lacking a comment syntax.
That file is shared across every repo that inherits this parent, so the fix
belongs there, not in a per-repo override here.
