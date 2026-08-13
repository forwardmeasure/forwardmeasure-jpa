# ForwardMeasure JPA TODO

This is the active work list for `forwardmeasure-jpa`. Completed remediation
work remains documented in [remediation-manifest.md](remediation-manifest.md).
Completed verification work is retained here as an auditable checklist; the
unchecked items under **Later work** are the remaining backlog.

## P0 — Detailed core persistence tests

- [x] Add comprehensive tests directly to `forwardmeasure-jpa-core` for the
  public repository and service behavior owned by that module.

  The tests must use a concrete test entity, repository, and service and must
  exercise persistence against a real PostgreSQL Testcontainer. Mocks are not
  permitted. Individual behaviors must have individually named tests so that
  a failure identifies the broken contract without relying on one aggregated
  assertion method.

  ### Base repository CRUD

  - [x] Persist one entity and verify generated identifier assignment.
  - [x] Persist and flush one entity.
  - [x] Persist an `Iterable` of entities.
  - [x] Persist entities through the varargs overload.
  - [x] Find by identifier and optional identifier.
  - [x] Find by identifier with an explicit lock mode.
  - [x] Distinguish managed and detached entities with `isPersistent`.
  - [x] Merge a detached entity and verify its updated state.
  - [x] Delete a managed entity.
  - [x] Delete a detached entity.
  - [x] Delete by identifier, including the not-found result.
  - [x] Delete all entities and verify the affected-row count.
  - [x] List and stream all entities.
  - [x] Flush and detach explicitly.
  - [x] Reject null entities and identifiers consistently.

  ### Paging, sorting, and specifications

  - [x] Verify default identifier sorting.
  - [x] Verify explicit ascending and descending sorting.
  - [x] Verify offsets, limits, total counts, and empty pages.
  - [x] Verify specification filtering applies consistently to data and count
    queries.
  - [x] Verify valid nested persistent-property traversal.
  - [x] Reject unknown, empty, and non-association property-path segments.

  ### Identifier metadata

  - [x] Prove generic repository type resolution through an inherited
    repository hierarchy.
  - [x] Prove identifier discovery through the JPA metamodel.
  - [x] Prove repositories do not assume that the identifier property is named
    `id`.
  - [x] Fail clearly when a persistence context has not been supplied.
  - [x] Reject attempts to rebind a repository to a different persistence
    context.

  ### Audited entities

  - [x] Verify JPA `@PrePersist` assigns UUID, creation time, and update time.
  - [x] Verify explicit pre-persist values are preserved where required.
  - [x] Verify `@PreUpdate` advances `updatedAt` without changing `createdAt`
    or UUID.
  - [x] Find an audited entity by UUID.
  - [x] Find multiple audited entities by UUID collection.
  - [x] Verify empty UUID collection behavior.
  - [x] Check existence by UUID.
  - [x] Delete by UUID, including the not-found result.
  - [x] Verify optimistic-version advancement and stale-update rejection.

  ### Service layer

  - [x] Exercise every `AbstractBaseService` operation through a concrete
    service implementation.
  - [x] Exercise every `AuditedEntityService` operation through a concrete
    service implementation.
  - [x] Verify service methods own the expected transaction boundaries.
  - [x] Verify `streamAll` remains usable after the repository call returns by
    confirming its materialized-stream behavior.

  ### Async-task behavior

  - [x] Verify all lifecycle transitions, retry exhaustion, progress merging,
    deferred completion, result representation, cancellation, skipping, and
    lease handling.
  - [x] Verify converters, legacy status compatibility, unregistered task
    types, and task-type registration conflicts.
  - [x] Verify creation defaults, idempotency lookup, sequential uniqueness,
    and a concurrent idempotency-key race against PostgreSQL.
  - [x] Verify transaction rollback removes both task and actor writes.
  - [x] Verify resource/status queries, filtered count/list/page operations,
    dispatch selection, due retries, expired leases, and expiry deletion.
  - [x] Verify pessimistic locking serializes competing workers.
  - [x] Verify task status and inline/external result projections.
  - [x] Run without mocks; all persistence tests use real PostgreSQL
    Testcontainers.

  ### Completion gate

  - [x] Run the new core test suite independently.
  - [x] Run the complete 12-module `mvn clean verify` reactor once after the
    core suite is green.
  - [x] Record the test count and verification output in this document.

  Verification completed on 2026-08-13:

  - `forwardmeasure-jpa-core`: 32 tests, all green.
  - `forwardmeasure-jpa-async-task`: 44 tests, all green.
  - Complete 12-module reactor: 102 tests, all green, no skips, in 2m07s.
  - Full output:
    `/home/pn/Downloads/mvn.forwardmeasure-jpa-final-with-detailed-tests.out`.

## P0 — Aggregate coverage enforcement

- [x] Generate one cross-module HTML, XML, and CSV JaCoCo report from all
  production modules and cross-module contract-test execution data.
- [x] Exclude only generated JPA metamodel and Micronaut framework bytecode.
- [x] Enforce at least 85% line coverage and 75% branch coverage.
- [x] Fail when any production class is completely untested.
- [x] Run a clean reactor verification with the aggregate gate enabled and
  record the final metrics.

  Verification completed on 2026-08-13:

  - Complete 13-module reactor: 110 tests across 19 test classes, all green,
    with no failures, errors, or skips, in 2m08s.
  - Aggregate instruction coverage: 91.62% (4,614/5,036).
  - Aggregate line coverage: 91.68% (1,058/1,154).
  - Aggregate branch coverage: 75.00% (267/356).
  - Aggregate class coverage: 100.00% (51/51); no production class is
    completely untested.
  - Full output:
    `/home/pn/Downloads/mvn.forwardmeasure-jpa-coverage-clean-final.out`.
  - Reports:
    `forwardmeasure-jpa-coverage/target/site/jacoco-aggregate/`.

## Later work

- [ ] Add Flyway support as a separate migration adapter without changing the
  existing Liquibase contracts.
- [ ] Evaluate a separate DID integration layer without coupling DID semantics
  to the core entity model.
- [ ] Decide whether async-task terminal transitions must require the current
  processing-owner token. The existing contract uses `processingOwner` to
  authorize lease extension; completion and failure methods do not currently
  accept an owner token.
