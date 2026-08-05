<!-- FILE: phase4-04-edge-cases-and-failure-modes.md -->
# Phase 4 — Edge Cases and Failure Modes

## Scenario Matrix

| # | Scenario | Planned Handling | Actual Behavior | Status |
|---|----------|------------------|-----------------|--------|
| 1 | **Unregistered step type on import** | Validator checks each step's `stepType` against `StepExecutorRegistry.getTypes()`. Returns 400 with list of unknown types. | ✅ Implemented — `validateImport()` in `JobExportImportService` checks step types against registry. Test: `shouldRejectImportWithUnknownStepType` | HANDLED |
| 2 | **Missing credential name** | Validator resolves each `SECRET_REF` field against the CREDENTIAL table. Returns 400 listing unresolved credential names. | ✅ Implemented — validator checks credential refs exist in target instance | HANDLED |
| 3 | **Duplicate job name with unspecified mode** | Default import mode is `ERROR`. If job name exists, return 409 Conflict. | ✅ Implemented — controller checks for existing job before import; service uses ERROR as default mode. Test: `shouldRejectImportWithExistingJobNameInErrorMode` | HANDLED |
| 4 | **Malformed JSON in stepConfig during export** | Export treats `stepConfig` as opaque string, serialized verbatim. No 500 risk. | ⚠️ **Deviation** — implementation parses stepConfig into a Jackson ObjectNode on export. Malformed JSON will cause export to throw. This is a behavior change from the plan. | **PARTIAL RISK** |
| 5 | **Malformed import payload** | Controller method parameter annotated `@Valid`. Jackson deserialization errors caught by Spring's default handler → 400. | ✅ Implemented — `@Valid` on controller parameter. Relies on Spring Boot's default error handling. | HANDLED |
| 6 | **Export mid-run** | Export reads only from JOB_DEFINITION + children tables — doesn't touch run state. No locking needed because orchestrator doesn't mutate job definitions during execution. | ✅ Works as designed — export is a read-only query on definition tables, independent of run state. | HANDLED |
| 7 | **Rollback to version with removed step type** | Rollback skips step-type validation (version was valid at save time). Logs warning if executor missing. | ⚠️ **Deviation** — rollback reuses the import path (`importJob`), which validates step types against the registry. If a step type is unregistered, rollback will fail with "unknown step type" error rather than logging a warning and proceeding. | **NOT HANDLED AS PLANNED** |
| 8 | **Import with cycle in dependencies** | Validator runs Kahn's algorithm on dependency graph before any DB writes. Returns 400. | ✅ Implemented — uses DFS-based cycle detection (not Kahn's, but same effect). Test: `shouldRejectImportWithCircularDependencies` | HANDLED |
| 9 | **Self-referential dependency** | Validator checks that no edge has `stepName == dependsOnStepName`. Returns 400. | ✅ Implemented — validator rejects self-referential edges | HANDLED |
| 10 | **Dependency references non-existent step** | Validator checks every `dependsOnStepName` matches a step name in the import document's steps array. Returns 400. | ✅ Implemented — name resolution fails with error if referenced step doesn't exist | HANDLED |
| 11 | **Duplicate step names within job** | Validator checks for duplicate `stepName` values. Returns 400. UNIQUE(JOB_ID, STEP_NAME) constraint added in V10. | ✅ Implemented — validator rejects duplicates. Note: DB-level unique constraint on (JOB_ID, STEP_NAME) should be verified as present. | HANDLED |
| 12 | **Import with empty steps array** | Validator requires at least one step. Returns 400. | ⚠️ Needs verification — check if `validateImport()` enforces non-empty steps array | NEEDS VERIFICATION |
| 13 | **Version number overflow** | NUMBER(10) max ~9.2 billion. Not actionable in practice (~25K updates/day for 100 years). | ✅ Theoretical only — no action needed | HANDLED (N/A) |
| 14 | **Export of job with no schedule** | Export omits the schedule key entirely when null (`@JsonInclude(NON_NULL)`). Import treats missing schedule as "no schedule". | ✅ Implemented — `JobExport` uses `@JsonInclude(NON_NULL)` at class level. Null schedule is omitted from output. | HANDLED |
| 15 | **Concurrent import of same job name** | Import wrapped in single transaction. UNIQUE constraint on `JOB_NAME` fires at commit time. Losing transaction rolls back with `DataIntegrityViolationException`. Controller catches → 409 Conflict. | ⚠️ Relies on Spring `@Transactional` + DB unique constraint. Explicit exception handling for concurrent import should be verified in controller code. | NEEDS VERIFICATION |
| 16 | **Global env var import by non-ADMIN user** | Import executor checks `isGlobal` flag. If true and user lacks ADMIN role, skips variable with warning. | ⚠️ Needs verification — check if `importJob()` has admin role check for global env vars | NEEDS VERIFICATION |
| 17 | **YAML import with anchor/alias references** | Jackson's `YamlFactory` resolves anchors/aliases natively into shared object graph. Works correctly since DTOs are records (immutable, no circular refs). | ✅ Works as designed — YAML parsing is handled by Jackson. Note: the current import endpoint only accepts JSON (`POST /import`), so YAML import is not currently supported via the API. | HANDLED (N/A) |
| 18 | **Import document from future format version** | Validator checks `format_version` ≤ max supported version ("1.0"). Returns 400. | ✅ Implemented — validator rejects unsupported format versions | HANDLED |

## Severity Classification

| Severity | Scenarios | Notes |
|----------|-----------|-------|
| **Critical (action needed)** | 7 | Rollback fails when step type is unregistered — should skip validation for rollback path |
| **High (should fix)** | 4, 16 | Malformed stepConfig breaks export; global env var security check needs verification |
| **Medium (verify and confirm)** | 12, 15 | Empty steps validation and concurrent import handling need code review confirmation |
| **Low / N/A** | 13, 17 | Theoretical or not applicable to current implementation |

## Recommended Follow-Up Actions

### 1. Rollback step-type validation bypass (Scenario 7) — Critical

The rollback path reuses `importJob()` which validates all step types against the registry. This means rolling back to a version that uses an unregistered step type will fail, even though the version was valid when saved.

**Fix options:**
- Add a `skipStepTypeValidation` flag to the import method, used only by rollback
- Create a dedicated `restoreFromVersion()` method in `JobVersionService` that bypasses validation
- Catch the validation error in rollback and log a warning instead of failing (less safe)

### 2. Malformed stepConfig export safety (Scenario 4) — High

Since `stepConfig` is parsed into an ObjectNode on export, malformed JSON in the CLOB will cause the entire export to fail with a Jackson exception.

**Fix options:**
- Wrap the parse in a try-catch; if parsing fails, store as a string with a `__parseError` marker
- Fall back to string representation for that step's config and log a warning
- Document this as a known limitation: "export requires well-formed stepConfig JSON"

### 3. Global env var security (Scenario 16) — High

Verify whether the import path checks user role before creating global environment variables. If not, any authenticated user could create system-wide env vars through the import endpoint.

**Fix:** Add role check in `importJob()` for `isGlobal: true` env vars. Skip with warning if non-ADMIN.

### 4. Empty steps validation (Scenario 12) — Medium

Verify that `validateImport()` rejects imports with an empty or null steps array. A job with no steps is vacuously successful but meaningless.

**Fix:** Add explicit check: `if (steps == null || steps.isEmpty()) errors.add("Job must have at least one step")`.

### 5. Concurrent import handling (Scenario 15) — Medium

Verify that the controller catches `DataIntegrityViolationException` from concurrent imports and returns a 409 response rather than a 500.

**Fix:** Add explicit exception handler in controller or global `@ControllerAdvice`.

## Additional V10 constraint for scenario 11

```sql
-- Verify this constraint exists on JOB_STEP (add if missing)
ALTER TABLE JOB_STEP ADD CONSTRAINT JOB_STEP_JOB_NAME_UK UNIQUE (JOB_ID, STEP_NAME);
```

**Note:** The current schema uses `STEP_NAME` as a VARCHAR2(200) on JOB_STEP. This constraint should be verified as present — the validator rejects duplicates at the application level, but the DB-level constraint provides defense in depth.
