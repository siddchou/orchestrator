# Phase 1 — Pluggable Step-Type Architecture (Overview)

## Scope Statement

Phase 1 opens the step-type system from a closed enum to an open String-based SPI, enabling new step types to be registered without modifying engine core, API controllers, or DB schema. The implementation is **complete** on the `plan3-phase2-ui` branch.

### What Was Blocked

The original codebase had:
1. A `StepType` Java enum with exactly 5 values, mirrored by an Oracle CHECK constraint on `JOB_STEP.STEP_TYPE`
2. A `StepExecutorFactory` that dispatched via switch-on-enum
3. A minimal `StepResult` of `(boolean, int, String)` — no structured outputs for downstream templating

### What Phase 1 Delivered

| Capability | Before | After |
|------------|--------|-------|
| Type system | Closed enum (5 values) + DB CHECK constraint | Open String + registry dispatch; V6 dropped the constraint |
| Dispatch | `StepExecutorFactory` switch statement | `StepExecutorRegistry` with `ConcurrentHashMap<String, StepExecutor>` |
| Context | `ExecutionContext` (Lombok @Data) | `StepContext` (builder-pattern, includes CredentialResolver, LogSink, upstreamOutputs) |
| Result | `(boolean success, int exitCode, String log)` | `StepResult` record with status enum, outputs map, execution time, backward-compat methods |
| Config schema | None — each executor parses its own typed config | `StepConfigSchema` + `FieldDefinition` per executor; consumed by orchestrator for pre-execute validation |
| Plugin loading | None | `PluginScanner` via ServiceLoader + URLClassLoader per JAR |
| Retry policy | Hardcoded in orchestrator | Per-executor `defaultRetryPolicy()` applied by orchestrator |
| Step count | 5 (ENV_SETUP, LOG_CLEANUP, JAVA_EXEC, SFTP, ARCHIVE) | 8 (+ HTTP_CALL, SHELL_EXEC, DB_QUERY) |

## What This Phase Does NOT Do

- Does not implement DAG execution (Phase 3) — `upstreamOutputs` wired as empty map
- Does not build UI forms (Phase 2) — schema is produced but not consumed by frontend
- Does not hot-reload plugins at runtime — `PluginScanner` runs once on `ApplicationReadyEvent`
- Does not add full schema-driven validation — pre-execute check is presence-only

## Table of Contents

| # | File | Purpose |
|---|------|---------|
| 1 | [phase1-code-review-findings.md](phase1-code-review-findings.md) | Ground-truth findings from code inspection |
| 2 | [phase1-00-overview.md](phase1-00-overview.md) | This file — scope, TOC, effort estimate |
| 3 | [phase1-01-interfaces-and-data-model.md](phase1-01-interfaces-and-data-model.md) | Full SPI type definitions with design rationale |
| 4 | [phase1-02-task-breakdown.md](phase1-02-task-breakdown.md) | Numbered, PR-sized task list with dependencies |
| 5 | [phase1-03-migration-strategy.md](phase1-03-migration-strategy.md) | Flyway SQL + rollback + backward-compat plan |
| 6 | [phase1-04-plugin-loading-design.md](phase1-04-plugin-loading-design.md) | Two architectural options for external plugin jars |
| 7 | [phase1-05-edge-cases-and-failure-modes.md](phase1-05-edge-cases-and-failure-modes.md) | Failure scenarios and required handling |
| 8 | [phase1-06-testing-plan.md](phase1-06-testing-plan.md) | Unit + integration test matrix + regression checklist |

## Implementation Status

**Phase 1 is COMPLETE.** All 17 tasks from the task breakdown have been implemented, all gap analysis fixes (Fix #1–#12) are incorporated in code, and the test suite passes with 253 tests (+25 new).

| Area | Status | Verified In |
|------|--------|-------------|
| SPI interfaces (`engine.spi.*`) | ✅ Complete — 9 classes | `src/main/java/.../engine/spi/` |
| StepExecutorRegistry + PluginScanner | ✅ Complete | `StepExecutorRegistry.java`, `PluginScanner.java` |
| JobStep entity (plain String stepType) | ✅ Complete — overloaded setters, no AttributeConverter | `JobStep.java:30` |
| Legacy executor migration (5 executors) | ✅ Complete — all implement new SPI | `engine/executors/` |
| New executors (HTTP_CALL, SHELL_EXEC, DB_QUERY) | ✅ Complete | `HttpCallStepExecutor.java`, `ShellExecStepExecutor.java`, `DbQueryStepExecutor.java` |
| Orchestrator wiring (registry dispatch, retry, validation) | ✅ Complete | `JobExecutionOrchestrator.java:127,156,245` |
| V6 migration (relax STEP_TYPE constraint) | ✅ Complete | `V6__relax_step_type_constraint.sql` |
| Pre-execute required-field validation | ✅ Complete — presence-only at line 245–274 | `JobExecutionOrchestrator.java:245-274` |
| Plugin development docs | ✅ Complete | `../../docs/plugin-development.md` |
| Test suite | ✅ 253 tests passing, +25 new | See phase1-06-testing-plan.md |

## Effort Estimate (Retrospective)

> All tasks completed within estimates.

| Task Group | Estimated Days | Notes |
|------------|---------------|-------|
| Core interfaces (StepExecutor v2, StepContext, StepResult v2, StepConfigSchema) | 1.5 | 9 classes in `engine.spi` package |
| StepExecutorRegistry refactor (from Factory) | 1.0 | Registry + test rewrite from factory test |
| JobStep entity update | 0.5 | Plain String field with overloaded setters |
| Migrate ENV_SETUP executor | 0.5 | Simplest: validates JAVA_HOME, sets context fields |
| Migrate LOG_CLEANUP executor | 0.5 | File pattern matching, no external deps |
| Migrate ARCHIVE executor | 0.5 | Commons Compress usage |
| Migrate JAVA_EXEC executor | 1.0 | Most complex: process management, timeout, shutdown hook |
| Migrate SFTP executor | 1.0 | Credential decryption via resolver, SSH client lifecycle |
| Implement HTTP_CALL executor | 1.5 | HttpClient API integration |
| Implement SHELL_EXEC executor | 1.0 | ProcessBuilder pattern |
| Implement DB_QUERY executor | 1.5 | JdbcTemplate, read-only whitelist |
| GET /api/step-types endpoint + controller | 0.5 | Wraps registry's listAll() |
| Pre-execute required-field validation | 1.0 | Presence-only check in orchestrator |
| Flyway migration (relax CHECK constraint) | 0.5 | Single ALTER TABLE |
| Plugin development documentation | 0.5 | Prose only |
| Integration test (multi-step job mixing executors) | 1.0 | H2 test DB with mixed step types |
| Regression verification | 0.5 | Full existing test suite green |
| **Total** | **~15 days** | One senior engineer, ~3 weeks |
