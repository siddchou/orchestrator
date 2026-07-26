# Phase 1 — Gap Analysis & Fixes

> Reviewed against each other and against `phase1-code-review-findings.md` (ground truth). This file lists every real gap found, why it matters, and the concrete fix. Treat this as a patch to be applied to the other 7 files before Task 1 starts.

---

## Gap Summary

| # | Severity | Gap | File(s) Affected |
|---|----------|-----|-------------------|
| 1 | **Critical (bug)** | `StepTypeConverter` declared as `AttributeConverter<StepType, String>` but the entity field is declared `String` — type mismatch, won't compile | phase1-03-migration-strategy.md |
| 2 | **High** | No task exists for the `JobStep` entity change — it's designed in doc 03 but never appears in doc 02's task list | phase1-02-task-breakdown.md |
| 3 | **High** | Self-contradiction: doc 01 says executors receive pre-validated `resolvedParams`, then says config parsing stays inside executors for Phase 1 — two incompatible designs | phase1-01-interfaces-and-data-model.md |
| 4 | **High** | Edge Case #6 requires orchestrator-side schema validation as "Required Handling" — no task implements it | phase1-05-edge-cases-and-failure-modes.md, phase1-02-task-breakdown.md |
| 5 | **Medium** | Task 6's "deprecated factory alias, compiles without changes" claim isn't achievable once the interface is replaced rather than extended | phase1-02-task-breakdown.md |
| 6 | **Medium** | Ambiguous whether `engine.spi.StepExecutor` is a new interface or an in-place edit of `engine.StepExecutor` — doc 02 Task 1 says one thing, doc 01's code says another | phase1-01, phase1-02 |
| 7 | **Medium** | Task 8 never assigns who builds `CredentialResolver` and `LogSink` — Task 5 depends on both existing | phase1-02-task-breakdown.md |
| 8 | **Low** | Flyway `V6` assumed free, but only V1 and V3 were confirmed — V2/V4/V5 unaccounted for | phase1-03-migration-strategy.md |
| 9 | **Low** | `GET /api/step-types` has no stated auth requirement, inconsistent with the rest of the JWT/RBAC'd API | phase1-02-task-breakdown.md |
| 10 | **Low** | `DB_QUERY` executor assumes `JdbcTemplate` is available; `spring-jdbc` wasn't explicitly confirmed on the classpath | phase1-code-review-findings.md, phase1-02-task-breakdown.md |
| 11 | **Low** | Effort estimate doesn't account for gaps #2 and #4's new tasks | phase1-00-overview.md |
| 12 | **Low** | Retry timing semantics unclear — does `executionTime` span all retry attempts or just the final one? | phase1-01, phase1-02 (Task 8) |

---

## Fix #1 (Critical) — StepTypeConverter type mismatch

**The bug**, as written in `phase1-03-migration-strategy.md`:
```java
@Convert(converter = StepTypeConverter.class)
@Column(name = "STEP_TYPE", nullable = false, length = 50)
private String stepType;   // ← field is String

public class StepTypeConverter implements AttributeConverter<StepType, String> {
    // ← but converter is declared for entity-attribute type StepType, not String
```
`@Convert` requires the annotated field's Java type to match the converter's first type parameter. Here the field is `String` but the converter is `AttributeConverter<StepType, String>` — this does not compile, and even if it did, JPA would reject the mismatch at bootstrap.

**Root cause**: the doc talks itself into two different designs in the same section ("keep field as String" vs. "use a converter with StepType") and ships both.

**Fix — drop the converter entirely.** The plan's own conclusion ("the orchestrator reads the raw string, not the enum, for dispatch") means a converter isn't needed. Use a plain `String` field and provide overloaded convenience methods on the entity for legacy callers that still pass the enum:

```java
// domain/entity/JobStep.java
@Column(name = "STEP_TYPE", nullable = false, length = 50)
private String stepType;

// Legacy-compatible setter — existing code calling setStepType(StepType.JAVA_EXEC) still works
public void setStepType(StepType type) {
    this.stepType = type.name();
}

// New setter for dynamically-registered types not in the enum
public void setStepType(String type) {
    this.stepType = type;
}

// Raw string getter — used by the orchestrator/registry for dispatch
public String getStepType() {
    return stepType;
}

// Best-effort enum view for legacy code that still wants StepType — returns null for new types
public StepType getStepTypeEnum() {
    try {
        return StepType.valueOf(stepType);
    } catch (IllegalArgumentException e) {
        return null;
    }
}
```

No `AttributeConverter` needed, no `@Enumerated`, no type mismatch. `StepType` enum itself is kept (unchanged) purely so any legacy call site passing `StepType.JAVA_EXEC` still compiles via the overload.

---

## Fix #2 (High) — Add the missing entity-migration task

Doc 03 designs the `JobStep` entity change in detail; doc 02 never turns it into a task. Insert this as a new task between the existing Task 6 and Task 7 (renumber Tasks 7–15 to 8–16 accordingly, or slot it in as "Task 6b" if you want to preserve existing numbering — recommend renumbering for clarity):

### Task 7 (new) — Update JobStep Entity for Open Step Type

| Field | Detail |
|-------|--------|
| **Files Touched** | `domain/entity/JobStep.java` |
| **Definition of Done** | Field changes from `@Enumerated(EnumType.STRING) private StepType stepType` to plain `private String stepType` (see Fix #1 above for exact code — no `AttributeConverter`). Overloaded `setStepType(StepType)` / `setStepType(String)` and `getStepTypeEnum()` added for legacy call-site compatibility. `StepType` enum itself is unchanged and retained. |
| **Test to Add** | Existing entity persists/loads a `JOB_STEP` row with `STEP_TYPE='JAVA_EXEC'` correctly via `getStepTypeEnum()`. New test: persist a row with `STEP_TYPE='HTTP_CALL'` (not in enum) — `getStepType()` returns `"HTTP_CALL"`, `getStepTypeEnum()` returns `null` without throwing. |
| **Depends On** | Task 6 (registry exists, so orchestrator can be updated to dispatch on the raw string in the same wave) |

This also directly unblocks Task 8 (orchestrator update), which needs `step.getStepType()` to return a raw `String` for `registry.get(...)` lookups — currently Task 8's definition of done doesn't say where that string comes from; it now does.

---

## Fix #3 (High) — Reconcile the config-parsing contradiction in doc 01

Doc 01's `StepExecutor` v2 section currently contains both of these, back to back:

> "Executors receive resolved, validated params — they don't parse JSON themselves."

> "The config parsing... moves into the executor's own logic initially (to keep changes minimal)."

These can't both be Phase 1's design. Given the plan's stated risk posture (byte-for-byte identical execution, minimal changes per migrated executor), **Phase 1 should keep config parsing inside each executor**, and treat `getConfigSchema()` as descriptive metadata only — consumed by Phase 2's UI and the `/api/step-types` endpoint, not enforced by the orchestrator yet.

**Corrected rationale paragraph** (replaces the contradictory one in doc 01, section 6):

> `getConfigSchema()` in Phase 1 is a **descriptive contract only** — it tells the UI (Phase 2) and the `/api/step-types` endpoint what fields exist, their types, and which are required. It is not yet enforced by the orchestrator before `execute()` is called. Each executor continues to parse `step.getStepConfig()` (the JSON CLOB) into its own typed record exactly as it does today — this is why migration is "byte-for-byte identical." Orchestrator-side enforcement (rejecting a run before execution if required fields are missing) is added in Fix #4 below as a lightweight, separate concern — not full type coercion, just presence-checking, to avoid scope creep into a second config-parsing layer that could drift from what executors actually expect.

This also resolves Gap #6 in the edge-cases doc more precisely — see Fix #4.

---

## Fix #4 (High) — Add the missing config-validation task

Edge Case Scenario 6 (`phase1-05-edge-cases-and-failure-modes.md`) requires:

> "The orchestrator validates config JSON against the schema **before** calling execute()."

No task implements this. Per Fix #3, scope it down to presence-checking only (not full type/enum validation — that's a natural Phase 2 follow-up once the UI is actually generating config from the schema and type mismatches become possible to introduce). Add as a new task:

### Task 9 (new, renumbered) — Pre-Execute Required-Field Validation

| Field | Detail |
|-------|--------|
| **Files Touched** | `engine/JobExecutionOrchestrator.java` |
| **Definition of Done** | Before calling `executor.execute(ctx)`, orchestrator parses `step.getStepConfig()` as generic JSON (`Map<String,Object>` via existing `JsonParser`), and checks that every `FieldDefinition` marked `required=true` in `executor.getConfigSchema()` has a non-null, non-blank value present. On failure, returns `StepResult.failure("Missing required config field(s): [...]", Duration.ZERO)` without invoking the executor — same failure path as Scenario 2 (unregistered type), so downstream handling (`continueOnFailure`, DB persistence) is unchanged. This is presence-only — it does not validate types, enum membership, or field formats; that logic stays in each executor as it does today. |
| **Test to Add** | Step with `STEP_CONFIG` missing a required field (e.g. SFTP config with no `host`) → orchestrator returns FAILED with the "Missing required config field(s)" message, **executor's `execute()` is never called** (verify via mock/spy). Step with all required fields present → executor is invoked normally. |
| **Depends On** | Task 7 (needs `getStepType()` returning raw string for registry lookup), Task 8 (orchestrator's new dispatch flow) |

Update the **Edge Case Scenario 6** row's "Required Handling" column to point at this task by name instead of describing it in the abstract.

---

## Fix #5 (Medium) — Correct the "deprecated factory alias" claim

Task 6 currently claims:

> "Old `StepExecutorFactory` class retained as deprecated alias that delegates to registry, so existing test code (`StepExecutorFactoryTest`) compiles without changes."

This isn't achievable as stated. Per Fix #6 below, `engine.spi.StepExecutor` is a **new, separate interface** from `engine.StepExecutor` — meaning once the 5 executors are migrated to implement only the new interface, zero beans implement the old one, and `StepExecutorFactory`'s `List<StepExecutor>` (old-interface) constructor injection resolves to an **empty list**. Any "alias" would return nothing for every `resolve()` call — worse than not having one, since it fails silently instead of failing loudly.

**Fix — replace, don't alias.** Update Task 6's Definition of Done:

> Old `engine.StepExecutorFactory` and `engine.StepExecutor` are marked `@Deprecated` in a Javadoc comment (for discoverability) but are **not** kept functional — nothing wires them anymore. `StepExecutorFactoryTest.java` is **rewritten** (not zero-diff) to test `StepExecutorRegistry` directly, preserving the same 5 assertions (one per legacy step type resolving to the correct executor class) against the new API. The old test file's *intent* is preserved; its *code* is not left unchanged.

Update Task 15's regression checklist row for "Factory dispatch" accordingly:

| Step Type | Existing Test File | Pass Criterion |
|-----------|--------------------|-----------------|
| Factory dispatch | `engine/StepExecutorFactoryTest.java` → rewritten as `engine/spi/StepExecutorRegistryTest.java` | Same 5 assertions (one per legacy step type resolves to the correct executor class), now expressed against `StepExecutorRegistry.get(String)` instead of the deprecated `StepExecutorFactory.resolve(StepType)` |

---

## Fix #6 (Medium) — Resolve the interface identity ambiguity

Task 1 in doc 02 says:

> **Files Touched**: `engine/StepExecutor.java` (add new methods with defaults) ...

But doc 01's actual code declares the v2 interface in a different package:

```java
package com.novakai.orchestrator.engine.spi;
public interface StepExecutor { ... }
```

...which is a distinct fully-qualified class from the existing `com.novakai.orchestrator.engine.StepExecutor` found in code review. These are not the same file, so "add new methods with defaults" (implying an in-place, backward-compatible edit) is the wrong framing — the actual artifact being created is a **new interface in a new package**, and the 5 executors are being **switched over** to implement it instead.

**Fix — make this explicit in Task 1**:

> **Files New** (not "Files Touched"): `engine/spi/StepExecutor.java`, `engine/spi/StepContext.java`, `engine/spi/StepResult.java`, `engine/spi/StepConfigSchema.java`, `engine/spi/FieldDefinition.java`, `engine/spi/FieldType.java`, `engine/spi/RetryPolicy.java`, `engine/spi/StepStatus.java` — all new files in a new `engine.spi` package.
> **Files Touched**: `engine/executors/EnvSetupStepExecutor.java` — changed to implement `engine.spi.StepExecutor` instead of `engine.StepExecutor` (import change, method signature change, `getSupportedType()` → `getType()` returning `.name()` of the old enum value as a string).
> The old `engine.StepExecutor` interface file is left in place, untouched, but no longer implemented by any active bean after this task completes (see Fix #5 for its fate).

This also removes the ambiguity in Design Principle #1 of doc 01 ("we extend, not replace") — correct that to: *"The **contract** (what an executor conceptually does) is preserved and evolved; the **interface type** is a clean replacement in a new package, not an in-place edit. This keeps the migration a series of small, isolated PRs rather than one big-bang interface change."*

---

## Fix #7 (Medium) — Assign ownership of CredentialResolver and LogSink construction

Task 5 (SFTP migration) assumes `StepContext.getCredentials().resolve(credentialRef)` and the live log queue bridge already work, but no task states who builds these. Add to **Task 8's** Definition of Done (orchestrator update):

> Orchestrator builds the `StepContext` passed to every executor, including:
> - `credentials`: a `CredentialResolver` lambda — `ref -> decryptionService.decrypt(credentialRepo.findByCredentialRef(ref).orElseThrow(() -> new CredentialNotFoundException(ref)).getCredValue())` — constructed once per step execution using the orchestrator's existing injected `JobCredentialRepository` and `CredentialDecryptionService`.
> - `logSink`: `new StepContext.LogSink(jobLaunchService.getLiveLogQueue(runId))`, bridging to the existing per-run queue so `LogStreamController`'s SSE reads are unaffected.
> - `upstreamOutputs`: empty map in Phase 1 (populated starting Phase 3 once DAG execution exists).

---

## Fix #8 (Low) — Verify the actual next Flyway version before Task 12

Code review confirmed V1 (`job_definition`) and V3 (`job_credential`) migrations exist but never enumerated the full `db/migration/` directory — V2, V4, and V5 are unaccounted for. Add as an explicit pre-check inside Task 12:

> **Before writing `V6__relax_step_type_constraint.sql`**: list `src/main/resources/db/migration/` and confirm the highest existing version number. Name the new migration `V{N+1}__relax_step_type_constraint.sql` using the actual next number — do not assume V6 is free.

---

## Fix #9 (Low) — Add auth requirement to the step-types endpoint

Task 7 (`GET /api/step-types`) has no stated access control, while the rest of the API is JWT/RBAC'd. Add to Definition of Done:

> Endpoint requires a valid JWT (same filter chain as other `/api/**` routes) but is accessible to any authenticated role (`ADMIN` or `USER`) — it's read-only metadata needed by any user building a job, not a privileged operation.

---

## Fix #10 (Low) — Confirm `spring-jdbc` / `JdbcTemplate` availability before Task 11

Code review's dependency table confirms Oracle/H2 drivers and Flyway but doesn't explicitly confirm `spring-boot-starter-jdbc` or `spring-boot-starter-data-jpa` (which transitively provides `JdbcTemplate`). Add a pre-check to Task 11:

> **Before implementation**: confirm `JdbcTemplate` is available as an autowireable bean (check `pom.xml` for `spring-boot-starter-data-jpa` or `spring-boot-starter-jdbc`). If only a raw `DataSource` is available, construct `JdbcTemplate` manually in the executor's `@Configuration` rather than assuming it's already a bean.

---

## Fix #11 (Low) — Revised effort estimate

Updated `phase1-00-overview.md` effort table with the two new tasks from Fixes #2 and #4:

| Task Group | Estimated Days | Change |
|------------|----------------|--------|
| Core interfaces (StepExecutor v2, StepContext, StepResult v2, StepConfigSchema) | 1.5 | unchanged |
| StepExecutorRegistry refactor (from Factory) — now includes rewriting StepExecutorFactoryTest per Fix #5 | 1.0 | +0.5 (was 0.5) |
| JobStep entity update (new — Fix #2) | 0.5 | **new** |
| Migrate ENV_SETUP executor | 0.5 | unchanged |
| Migrate LOG_CLEANUP executor | 0.5 | unchanged |
| Migrate ARCHIVE executor | 0.5 | unchanged |
| Migrate JAVA_EXEC executor | 1.0 | unchanged |
| Migrate SFTP executor | 1.0 | unchanged |
| Implement HTTP_CALL executor | 1.5 | unchanged |
| Implement SHELL_EXEC executor | 1.0 | unchanged |
| Implement DB_QUERY executor (incl. Fix #10 pre-check) | 1.5 | unchanged |
| GET /api/step-types endpoint + auth (Fix #9) | 0.5 | unchanged |
| Pre-execute required-field validation (new — Fix #4) | 1.0 | **new** |
| Flyway migration (relax CHECK constraint, incl. Fix #8 version check) | 0.5 | unchanged |
| Plugin development documentation | 0.5 | unchanged |
| Integration test (multi-step job mixing old + new executors) | 1.0 | unchanged |
| Regression verification (full existing test suite green) | 0.5 | unchanged |
| **Total** | **~15 days** | +2 days vs. original ~13 |

---

## Fix #12 (Low) — Clarify retry timing semantics

Task 8's description ("wraps execute() call with `System.nanoTime()`") is ambiguous about whether timing wraps a single attempt or the full retry loop, while the testing plan expects `executionTime includes retry overhead` (i.e., cumulative). Clarify in Task 8:

> Timing wraps the **entire retry loop** for a step, not each individual attempt — `startTime` is captured before the first attempt, `executionTime` is computed after the loop exits (whether by success or exhausting `maxAttempts`). Individual attempt durations are not tracked separately in Phase 1 (no per-attempt telemetry yet — that's a natural Phase 6 observability addition).

---

## What Was Verified as Correct (no changes needed)

To be clear about scope — most of the plan holds up well under review:
- The `RetryPolicy.fixed()`/`retries()` math is internally consistent and matches the testing plan's expected values.
- `HttpCallStepExecutor`'s use of `RestClient` (not `WebClient`) is correctly reconciled against the confirmed absence of `spring-webflux`.
- The SSE compatibility bridge (`StepContext.getLiveLogQueue()`) correctly preserves the exact access pattern executors use today (`ctx.getLiveLogQueue().add(line)`).
- Plugin loading Option A's recommendation is well-grounded — it correctly observes that the existing `StepExecutorFactory` already relies on Spring's `List<StepExecutor>` auto-collection, so no new plugin-loading code is actually required for the simple variant.
- The task dependency graph (which tasks block which) is structurally sound aside from the two missing tasks patched in Fixes #2 and #4 above — once those are inserted (Task 7 and Task 9, with everything downstream renumbered), the ordering logic holds.
