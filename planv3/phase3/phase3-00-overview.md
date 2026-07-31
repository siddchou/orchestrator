# Phase 3 — Workflow Flexibility (DAG, Branching, Templating)

## Scope

Phase 3's core features are **already implemented**: DAG-based concurrent execution, conditional edges (ON_SUCCESS/ON_FAILURE/ALWAYS), and parameter templating (`${job.param.X}`, `${step.<id>.output.X}`, `${env.X}`).

This phase now focuses on **auditing correctness**, fixing 4 identified bugs, verifying thread safety under concurrent execution, and adding e2e test coverage.

**Grounded in code review findings:**
- `DagExecutionEngine.java` (672 lines) exists with Kahn's cycle detection, semaphore-bounded concurrency, CountDownLatch sync
- `ParamResolver.java` handles all three template reference types plus default value syntax
- `JOB_STEP_DEPENDENCY` table created via V8 migration, backfilled via V9
- `JobStepDependency` entity + repository complete with edge condition enum
- UI DAG canvas (`RunDagCanvasComponent`) integrated into run detail page

## Remaining Scope

| Area | Status | Action |
|------|--------|--------|
| DAG engine core | Implemented | Fix BUG-1 (empty upstreamOutputs), BUG-2 (SKIPPED as FAILED) |
| Parameter resolver | Implemented | Verify wiring is correct end-to-end |
| Dependency model | Implemented | No changes needed — join table approach working |
| Migrations V8/V9 | Deployed | Verify backfill data integrity |
| Thread safety | Partial concern | Audit envVars sharing, CredentialResolver cipher reuse |
| Cancellation under DAG | Partial | Fix BUG-4 (CANCELLED status) |
| E2E concurrency test | Missing | Add diamond DAG timing test to prove parallelism |
| UI DAG visualization | Implemented | 18/20 tasks complete per IMPLEMENTATION_STATUS.md |

## Assumptions

1. **[CONFIRMED]** Diamond-shaped DAGs are a real requirement — the join table dependency model is chosen and implemented because multiple upstream dependencies with per-edge conditions are needed.
2. **[CONFIRMED]** Oracle remains the primary database; all SQL uses Oracle dialect (`NUMBER`, `VARCHAR2`, `CLOB`).
3. **[CONFIRMED]** The Angular frontend for DAG visualization exists — `RunDagCanvasComponent` renders read-only DAG for run details.
4. **[CONFIRMED]** Existing jobs continue working after migration; V9 backfill converts `stepOrder` chains into dependency chains automatically.
5. **[CONFIRMED]** The `continueOnFailure=N/Y` column maps to edge conditions: V9 respects `continueOnFailure=Y` → `ALWAYS` condition.

## Table of Contents

1. [phase3-code-review-findings.md](phase3-code-review-findings.md) — Codebase state + bugs found
2. [phase3-00-overview.md](phase3-00-overview.md) — This file: scope, assumptions, effort
3. [phase3-01-dag-engine-design.md](phase3-01-dag-engine-design.md) — Existing architecture audit + bug fixes
4. [phase3-02-task-breakdown.md](phase3-02-task-breakdown.md) — Fix/improve tasks with DoD
5. [phase3-03-migration-strategy.md](phase3-03-migration-strategy.md) — V8/V9 verification
6. [phase3-04-concurrency-and-thread-safety.md](phase3-04-concurrency-and-thread-safety.md) — Thread safety audit
7. [phase3-05-edge-cases-and-failure-modes.md](phase3-05-edge-cases-and-failure-modes.md) — Edge cases in implemented code
8. [phase3-06-testing-plan.md](phase3-06-testing-plan.md) — Test gaps + e2e plan

## Effort Estimate

| Task Area | Complexity | Story Points | Notes |
|-----------|------------|--------------|-------|
| Fix BUG-1 (upstreamOutputs) | Medium | 2 | Core fix — upstream data flow |
| Fix BUG-2 (SKIPPED status) | Low | 1 | Status enum correction |
| Fix BUG-3 (timing) | Low | 0.5 | Metric accuracy |
| Fix BUG-4 (CANCELLED) | Medium | 2 | New enum value + cancel path |
| Thread safety audit | Medium | 2 | envVars isolation, cipher verification |
| E2E concurrency test | Medium | 3 | Diamond DAG timing proof |
| Legacy cleanup | Low | 0.5 | Stale comments |
| **Total** | | **~10** | Down from ~30 (work already done) |

## Future Work (Out of Scope)

- **Sub-workflow composition (`SUB_JOB` step type):** A step that triggers a nested job run and blocks on completion. Deferred to a stretch phase after core DAG is stable.
- **Dependency CRUD API:** Dedicated endpoint for editing dependencies from the UI graph editor. Optional — current UI uses bulk step update.
