# Phase 1 — Code Review Findings

> **Date:** 2026-07-30
> **Branch:** `plan3-phase2-ui`
> **Status:** Phase 1 implementation is COMPLETE in code. This document records ground-truth findings from inspecting the actual implemented code, not the pre-implementation plan assumptions.

## Executive Summary

Phase 1 — Pluggable Step-Type Architecture — is fully implemented. The original plan assumed a closed enum-based type system; the codebase now has an open String-based SPI with registry dispatch, plugin loading, config schema support, and pre-execute validation. All 8 executors (5 legacy + 3 new) are migrated to the new interface. The test suite contains 253 passing tests including 25 new ones for Phase 1.

## Build Configuration

| Property | Value | Source |
|----------|-------|--------|
| Spring Boot | 4.1.0 | `pom.xml` parent |
| Java | 21 (virtual threads) | `pom.xml` properties, `LogStreamController.java:38` |
| Oracle JDBC | ojdbc11 21.9.0.0 | `pom.xml` dependency |
| H2 | Test profile only | `pom.xml` scope=test |
| Flyway | core + oracle | `pom.xml` |
| Apache SSHD | 2.18.0 | `pom.xml` — SFTP executor |
| Commons Compress | 1.26.1 | `pom.xml` — Archive executor |
| Lombok | 1.18.32 | `pom.xml` |
| Jackson | databind (managed by Boot BOM) | `pom.xml` |
| JJWT | 0.12.7 | `pom.xml` — JWT auth |

## Database Migrations

| Version | File | Purpose |
|---------|------|---------|
| V1 | `V1__create_job_definition.sql` | JOB_DEFINITION, JOB_STEP tables; CHECK constraint on STEP_TYPE |
| V2 | `V2__create_job_run.sql` | JOB_RUN, JOB_RUN_STEP tables |
| V3 | `V3__create_schedule_and_credential.sql` | SCHEDULE, JOB_CREDENTIAL tables |
| V4 | `V4__create_app_user.sql` | APP_USER table |
| V5 | `V5__add_env_setup_to_job_definition.sql` | ENV_SETUP columns on JOB_DEFINITION |
| **V6** | `V6__relax_step_type_constraint.sql` | **Drops CHECK constraint on STEP_TYPE — opens type system** |
| V7 | `V7__add_multi_tenancy.sql` | Tenant support |
| V8 | `V8__add_step_dependencies.sql` | Step dependency columns |
| V9 | `V9__backfill_step_dependencies.sql` | Backfills dependencies from config JSON |
| V10 | `V10__add_job_definition_version.sql` | Job definition versioning |

**Next free migration version: V11**

## Findings by Area

### 1. Step Execution Dispatch

**Pre-implementation assumption:** A `StepExecutorFactory` with a switch-on-enum dispatch pattern.

**Actual code state:** Two-tier system:
- **`StepExecutorRegistry`** (`engine/spi/StepExecutorRegistry.java`) — Spring `@Component`, constructor-injects `List<StepExecutor>`, stores in `ConcurrentHashMap<String, StepExecutor>` for O(1) lookup by type string. Provides `get(String)` → Optional, `listAll()` → schemas, `registeredTypes()` → Set
- **`JobExecutionOrchestrator.executeStep()`** (line 127) — resolves executor via `registry.get(step.getStepType())`, failing with a descriptive error if unregistered

The old `StepExecutorFactory` interface still exists in `engine/StepExecutor.java` but is unimplemented — no class implements it after migration. The old factory test was rewritten as `StepExecutorRegistryTest`.

### 2. Step Type Resolution

**Pre-implementation assumption:** Closed enum requiring enum + DB constraint changes for each new type.

**Actual code state:** Open String-based system:
- **`JobStep.stepType`** (`domain/entity/JobStep.java:30`) — plain `String` field, no `@Enumerated`, no `AttributeConverter`
- Overloaded setters: `setStepType(StepType)` (stores `type.name()`), `setStepType(String)` (accepts any string)
- `getStepTypeEnum()` returns null for unrecognized values (no throw)
- **`StepType` enum** (`domain/enums/StepType.java`) — retained for backward compat only; contains ENV_SETUP, LOG_CLEANUP, JAVA_EXEC, SFTP, ARCHIVE (does NOT include HTTP_CALL, SHELL_EXEC, DB_QUERY)
- V6 migration already dropped the CHECK constraint

### 3. Step Configuration Storage

**Finding:** JSON CLOB blob, as originally assumed. `JobStep` has a `@Lob @Column STEP_CONFIG` field of type String. Each executor parses its own typed config record from this JSON inside `execute(StepContext)`. No per-type columns were added.

### 4. SFTP Credential Handling

**Encryption:** AES/GCM/NoPadding via `CredentialDecryptionService` (`engine/service/CredentialDecryptionService.java`)
- Key sourced from env var `ORCHESTRATOR_ENCRYPTION_KEY`, defaults to a hardcoded fallback (security concern for production)
- 12-byte IV, prepended to ciphertext, Base64-encoded
- Key padded to 32 bytes if shorter

**Resolution in executor:** SFTP executor no longer injects `JobCredentialRepository` directly. Instead, the orchestrator builds a `StepContext.CredentialResolver` lambda (`ref -> decryptionService.decrypt(credentialRepo.findByCredentialRef(ref)...getCredValue())`) and passes it into StepContext. The executor calls `context.getCredentials().resolve(ref)`.

### 5. SSE Log Streaming

**Mechanism:** Per-run `BlockingQueue<String>` managed by `JobLaunchService`, consumed by a virtual thread in `LogStreamController` (`api/controller/LogStreamController.java`)
- GET `/api/runs/{runId}/log-stream` produces `text/event-stream`
- Virtual thread polls queue with timeout, sends via `SseEmitter`
- New executors write to `StepContext.logSink.write(String)` which delegates to the same queue

### 6. Retry/Timeout Logic

**Retry:** Orchestrator wraps executor calls in a retry loop (`JobExecutionOrchestrator.java:156-180`) based on each executor's `defaultRetryPolicy()` — `maxAttempts` and `delayBetweenAttempts`. Interrupt handling between retries allows clean cancellation.

**Timing:** Execution time measured around the entire retry loop (not per-attempt). `System.nanoTime()` before first attempt, computed after loop exit.

**Timeout:** Individual executors handle their own timeouts:
- JavaExecStepExecutor: configurable `timeoutMinutes`, kills process on timeout
- HttpCallStepExecutor: uses `HttpClient` with request-level timeout
- ShellExecStepExecutor: configurable `timeoutSeconds`, destroys process on timeout

### 7. Pre-Execute Validation

**Implementation:** `JobExecutionOrchestrator.validateRequiredFields()` (lines 245-274) parses step config as generic JSON, checks schema for required fields present and non-blank. On failure: returns `StepResult.failure("Missing required config field(s): [...]", Duration.ZERO)` without invoking executor.

**Scope:** Presence-only validation. Does NOT validate types, enum membership, or field formats — that logic stays in each executor as it does today.

### 8. Plugin Loading

**Implementation:** `PluginScanner` (`engine/spi/PluginScanner.java`) listens for `ApplicationReadyEvent`, scans directory from `orchestrator.plugins.dir` property for JARs. Each JAR loaded via `URLClassLoader` + Java `ServiceLoader<StepExecutor>`. Classloaders tracked and closed on `@PreDestroy`.

## Contradictions vs Original Plan Assumptions

| # | Original Assumption | Actual Resolution | Impact |
|---|---------------------|-------------------|--------|
| 1 | StepType is a Java enum with 5 values, mirrored by DB CHECK constraint | V6 already dropped the CHECK constraint; entity uses plain String | No migration needed — already done |
| 2 | Factory pattern with switch-on-enum dispatch | Replaced with Registry + ConcurrentHashMap dispatch | Old factory deprecated, test rewritten |
| 3 | StepResult is (boolean, int, String) | Enriched to record with status enum, outputs map, execution time | Backward-compat methods provided |
| 4 | Config schema is a Phase 2 concern | Implemented in Phase 1 via `StepConfigSchema` + `FieldDefinition` | UI can consume schemas now |
| 5 | Plugin loading deferred to future phase | Implemented via `PluginScanner` with ServiceLoader | Both classpath and JAR-based loading supported |
| 6 | Retry logic lives in orchestrator only | Each executor declares `defaultRetryPolicy()`, orchestrator applies it | Policy is executor-aware, not global |

## Files Inventory — Phase 1 Artifacts

### SPI Interfaces (8 classes)
- `engine/spi/StepExecutor.java` — interface with getType(), getConfigSchema(), execute(StepContext), defaultRetryPolicy()
- `engine/spi/StepExecutorRegistry.java` — ConcurrentHashMap-based registry, Spring @Component
- `engine/spi/StepContext.java` — builder-pattern context replacing ExecutionContext
- `engine/spi/StepResult.java` — record with status, outputs, message, executionTime
- `engine/spi/StepConfigSchema.java` — record with stepType, displayName, field definitions
- `engine/spi/FieldDefinition.java` — record with name, label, type, required, defaultValue, enumValues, helpText
- `engine/spi/FieldType.java` — enum: STRING, NUMBER, BOOLEAN, ENUM, SECRET_REF, FILE_PATTERN, LIST_STRING
- `engine/spi/StepStatus.java` — enum: SUCCESS, FAILED, SKIPPED
- `engine/spi/RetryPolicy.java` — record with maxAttempts, delayBetweenAttempts

### Executors (8 implementations)
- `engine/executors/EnvSetupStepExecutor.java` — ENV_SETUP
- `engine/executors/LogCleanupStepExecutor.java` — LOG_CLEANUP
- `engine/executors/ArchiveStepExecutor.java` — ARCHIVE
- `engine/executors/JavaExecStepExecutor.java` — JAVA_EXEC
- `engine/executors/SftpStepExecutor.java` — SFTP
- `engine/executors/HttpCallStepExecutor.java` — HTTP_CALL (new)
- `engine/executors/ShellExecStepExecutor.java` — SHELL_EXEC (new)
- `engine/executors/DbQueryStepExecutor.java` — DB_QUERY (new)

### Supporting Classes
- `engine/spi/PluginScanner.java` — JAR-based plugin loading via ServiceLoader
- `api/controller/StepTypeController.java` — GET /api/step-types endpoint
- `engine/service/CredentialDecryptionService.java` — AES/GCM credential encryption/decryption

### Tests (16 test files)
- 7 SPI-level tests: Registry, PluginScanner, RetryPolicy, FieldDefinition, StepContext, StepResult, RegistryUnit
- 8 executor tests: one per executor + SleepStepExecutor and FailStepExecutor test helpers
- 1 mixed executor integration test
