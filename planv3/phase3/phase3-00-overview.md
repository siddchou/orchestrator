<!-- FILE: phase3-00-overview.md -->
# Phase 3 — Workflow Flexibility (DAG, Branching, Templating)

## Scope

Replace the linear step-execution model with a dependency-graph engine supporting concurrent execution of independent branches, conditional edges (ON_SUCCESS/ON_FAILURE/ALWAYS), and parameter templating resolved before each step executes.

**Grounded in code review findings:**
- Current loop: sequential `for` over steps sorted by `stepOrder` ([JobExecutionOrchestrator.java:70](src/main/java/com/novakai/orchestrator/engine/JobExecutionOrchestrator.java:70))
- Thread pool already exists (core=10, max=20) but is used one-Future-per-run — Phase 3 repurposes it for intra-run concurrency
- `StepResult.outputs` map and `StepContext.upstreamOutputs` are wired but empty — Phase 3 populates them
- Run endpoint accepts no body — Phase 3 adds a request body for runtime parameters

## Assumptions ([ASSUMED] markers)

1. **[ASSUMED]** Diamond-shaped DAGs (A→B, A→C, B+C→D) are a real requirement — the join table dependency model is chosen because multiple upstream dependencies with per-edge conditions are needed.
2. **[ASSUMED]** Oracle remains the primary database; all SQL uses Oracle dialect (`NUMBER`, `VARCHAR2`, `CLOB`).
3. **[ASSUMED]** The Angular frontend for DAG visualization (Phase 2b canvas) is deferred — Phase 3 focuses on backend engine + API only. Frontend dependency editing can use a simple JSON editor initially.
4. **[ASSUMED]** Existing jobs must continue working identically after migration; the backfill converts `stepOrder` chains into dependency chains automatically.
5. **[ASSUMED]** The `continueOnFailure=N/Y` column on JOB_STEP will be deprecated in favor of edge conditions, with a data migration that maps `continueOnFailure=Y` to default ON_SUCCESS edges (behavior-preserving).

## Table of Contents

1. [phase3-code-review-findings.md](phase3-code-review-findings.md) — Codebase state before Phase 3
2. [phase3-00-overview.md](phase3-00-overview.md) — This file: scope, assumptions, effort
3. [phase3-01-dag-engine-design.md](phase3-01-dag-engine-design.md) — DAG engine + ParamResolver architecture
4. [phase3-02-task-breakdown.md](phase3-02-task-breakdown.md) — PR-sized tasks with DoD
5. [phase3-03-migration-strategy.md](phase3-03-migration-strategy.md) — Flyway SQL + backfill
6. [phase3-04-concurrency-and-thread-safety.md](phase3-04-concurrency-and-thread-safety.md) — Thread safety analysis
7. [phase3-05-edge-cases-and-failure-modes.md](phase3-05-edge-cases-and-failure-modes.md) — Edge cases table
8. [phase3-06-testing-plan.md](phase3-06-testing-plan.md) — Unit + integration tests

## Effort Estimate

| Task Area | Complexity | Story Points | Notes |
|-----------|------------|--------------|-------|
| DB migration (V8, V9 backfill) | Low | 2 | Straightforward DDL + data migration |
| JOB_STEP_DEPENDENCY entity + repository | Low | 2 | New JPA entity, simple FKs |
| DagExecutionEngine (topological sort, concurrency) | High | 8 | Core new component |
| Edge condition evaluation | Medium | 3 | ON_SUCCESS/ON_FAILURE/ALWAYS logic |
| ParamResolver (regex-based templating) | Medium | 5 | Resolution order, error handling |
| API changes (run body with parameters) | Low | 2 | Controller + DTO update |
| Thread safety fixes (StepContext, log queue) | Medium | 3 | Concurrency primitives |
| Integration tests (diamond DAG, regression) | Medium | 5 | Test infrastructure for concurrency |
| **Total** | | **~30** | ~12-15 PRs |

## Future Work (Out of Scope)

- **Sub-workflow composition (`SUB_JOB` step type):** A step that triggers a nested job run and blocks on completion. This reuses Phase 1's SPI cleanly but adds recursive DAG execution, nested parameter scoping, and cross-run output propagation. Deferred to a stretch phase after core DAG is stable. Mention only: the `StepExecutor` interface can express this as one class; no engine changes are needed beyond allowing an executor to spawn child runs.
