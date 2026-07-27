# Phase 1 — Pluggable Step-Type Architecture (Overview)

## Scope Statement

The codebase already has a `StepExecutor` SPI and a `StepExecutorFactory` that auto-collects executor beans. The blocking points for extensibility are:

1. **Closed type system**: `StepType` is a Java enum with exactly 5 values, mirrored by an Oracle CHECK constraint on `JOB_STEP.STEP_TYPE`. Adding step type #6 requires touching the enum, the DB constraint, and potentially the factory's map key type.
2. **No config schema contract**: Each executor parses its own typed record (`JavaExecConfig`, etc.) from JSON. There is no machine-readable schema that a UI form generator can consume — Phase 2 depends on this.
3. **Minimal StepResult**: `(boolean, int, String)` carries no structured outputs (needed for Phase 3 templating: `${step.<id>.output.X}`).

This phase opens the type system so that registering a new step type requires only: one `StepExecutor` implementation class + one Spring bean registration. Zero changes to engine core, API controllers, or DB schema (beyond relaxing the CHECK constraint).

## What This Phase Does NOT Do

- Does not change how existing jobs execute — all 5 current executors are migrated with byte-for-byte identical execution logic.
- Does not implement DAG execution (Phase 3).
- Does not build UI forms (Phase 2) — only produces the schema contract they consume.
- Does not add hot-reload plugin loading — documents a classpath-based approach for v1.

## Table of Contents

| # | File | Purpose |
|---|------|---------|
| 1 | [phase1-code-review-findings.md](phase1-code-review-findings.md) | Ground-truth findings from code inspection |
| 2 | [phase1-00-overview.md](phase1-00-overview.md) | This file — scope, TOC, effort estimate |
| 3 | [phase1-01-interfaces-and-data-model.md](phase1-01-interfaces-and-data-model.md) | Full Java interface definitions with design rationale |
| 4 | [phase1-02-task-breakdown.md](phase1-02-task-breakdown.md) | Numbered, PR-sized task list with dependencies |
| 5 | [phase1-03-migration-strategy.md](phase1-03-migration-strategy.md) | Flyway SQL + rollback + backward-compat plan |
| 6 | [phase1-04-plugin-loading-design.md](phase1-04-plugin-loading-design.md) | Two architectural options for external plugin jars |
| 7 | [phase1-05-edge-cases-and-failure-modes.md](phase1-05-edge-cases-and-failure-modes.md) | Failure scenarios and required handling |
| 8 | [phase1-06-testing-plan.md](phase1-06-testing-plan.md) | Unit + integration test matrix + regression checklist |
| 9 | [phase1-07-gap-analysis-and-fixes.md](phase1-07-gap-analysis-and-fixes.md) | Cross-review of files 3–8: contradictions, a JPA type-mismatch bug, missing tasks, and their fixes — **read before starting implementation, several fixes are already folded into the files above** |

## Implementation Status

**Phase 1 is COMPLETE.** All 17 tasks from the task breakdown have been implemented, all gap analysis fixes (Fix #1–#12) are incorporated in code, and the test suite passes with 253 tests (+25 new). See individual documents for per-area verification details.

| Area | Status | Verified In |
|------|--------|-------------|
| SPI interfaces (`engine.spi.*`) | ✅ Complete — all 8 classes exist | `src/main/java/.../engine/spi/` |
| StepExecutorRegistry + PluginScanner | ✅ Complete | `StepExecutorRegistry.java`, `PluginScanner.java` |
| JobStep entity (plain String stepType) | ✅ Complete — overloaded setters, no AttributeConverter | `JobStep.java:30` |
| Legacy executor migration (5 executors) | ✅ Complete — all implement new SPI | `engine/executors/` |
| New executors (HTTP_CALL, SHELL_EXEC, DB_QUERY) | ✅ Complete | `HttpCallStepExecutor.java`, `ShellExecStepExecutor.java`, `DbQueryStepExecutor.java` |
| Orchestrator wiring (registry dispatch, retry, validation) | ✅ Complete | `JobExecutionOrchestrator.java:127,156,245` |
| V6 migration (relax STEP_TYPE constraint) | ✅ Complete | `V6__relax_step_type_constraint.sql` |
| Pre-execute required-field validation | ✅ Complete — presence-only at line 245–274 | `JobExecutionOrchestrator.java:245-274` |
| Plugin development docs | ✅ Complete — covers both loading methods, HELLO_WORLD example, API reference | `../../docs/plugin-development.md` |
| Test suite | ✅ 253 tests passing, +25 new | See phase1-06-testing-plan.md |

## Effort Estimate

> Revised after gap analysis (see `phase1-07-gap-analysis-and-fixes.md`) — two tasks were missing from the original estimate: the `JobStep` entity migration (designed in doc 03 but never costed) and orchestrator-side required-field validation (required by Edge Case Scenario 6 but never costed).

| Task Group | Estimated Days | Rationale |
|------------|---------------|-----------|
| Core interfaces (StepExecutor v2, StepContext, StepResult v2, StepConfigSchema) | 1.5 | New interfaces in `engine.spi` package; most fields map to ExecutionContext equivalents |
| StepExecutorRegistry refactor (from Factory) | 1.0 | Registry + `listAll()` for UI endpoint; **includes rewriting `StepExecutorFactoryTest` as `StepExecutorRegistryTest`** — old factory is deprecated, not kept functional (see gap-analysis Fix #5) |
| **JobStep entity update** | **0.5** | **New** — `stepType` field changes from enum-mapped to plain String with legacy-compatible overloads (gap-analysis Fix #2) |
| Migrate ENV_SETUP executor | 0.5 | Simplest executor: validates JAVA_HOME, sets context fields |
| Migrate LOG_CLEANUP executor | 0.5 | File pattern matching, no external deps |
| Migrate ARCHIVE executor | 0.5 | Commons Compress usage, straightforward config mapping |
| Migrate JAVA_EXEC executor | 1.0 | Most complex: process management, timeout, validation, shutdown hook |
| Migrate SFTP executor | 1.0 | Credential decryption, SSH client lifecycle, known-hosts |
| Implement HTTP_CALL executor | 1.5 | New: RestClient integration, headers/body/status handling |
| Implement SHELL_EXEC executor | 1.0 | ProcessBuilder pattern (reuse JavaExec process management ideas) |
| Implement DB_QUERY executor | 1.5 | New: datasource ref resolution, read-only whitelist, result set mapping; includes confirming `JdbcTemplate` availability first |
| GET /api/step-types endpoint + controller | 0.5 | Simple REST endpoint wrapping registry's `listAll()`; requires standard JWT auth like other `/api/**` routes |
| **Pre-execute required-field validation** | **1.0** | **New** — orchestrator checks required config fields against schema before invoking executor (gap-analysis Fix #4) |
| Flyway migration (relax CHECK constraint) | 0.5 | Single ALTER TABLE; low risk on Oracle. Confirm actual next free version number before naming the script (not assumed to be V6) |
| Plugin development documentation | 0.5 | Prose only, references existing patterns |
| Integration test (multi-step job mixing old + new executors) | 1.0 | Needs H2 test DB setup with mixed step types |
| Regression verification (full existing test suite green) | 0.5 | Run existing tests, fix any breakage from interface changes |
| **Total** | **~15 days** | One senior engineer, ~3 weeks |
