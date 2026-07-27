# Phase 1 — Migration Strategy

## Summary

No data migration is needed. The project has **no production data yet** — the test profile uses H2 in-memory with Flyway disabled (`flyway.enabled: false`, `ddl-auto: create-drop`). The only artifact required is a single Flyway migration for when the app deploys to Oracle.

## Verification Status

**COMPLETE.** All migration artifacts confirmed in codebase:

| Artifact | Expected | Found In Codebase | Match |
|----------|----------|-------------------|-------|
| JobStep.stepType field | Plain `String` with overloaded setters | `JobStep.java:30` — `private String stepType`, `setStepType(StepType)`, `setStepType(String)`, `getStepTypeEnum()` | ✅ Exact match |
| No AttributeConverter | No `@Convert` annotation, no converter class | Confirmed — no `@Convert` on stepType field | ✅ Matches |
| StepType enum retained | Enum unchanged for backward compat | `domain/enumeration/StepType.java` exists with 5 values | ✅ Matches |
| V6 migration file | Drops CHECK constraint on STEP_TYPE | `V6__relax_step_type_constraint.sql` — Oracle PL/SQL block that drops constraints matching `%STEP_TYPE%` | ✅ Matches |
| H2 test profile | Flyway disabled, ddl-auto: create-drop | Confirmed in test application.yml | ✅ Matches |

## What Changed

| Layer | Before | After | Impact |
|-------|--------|-------|--------|
| Entity `JobStep.stepType` | `@Enumerated(EnumType.STRING) StepType` | Plain `String` | JPA reads/writes raw string values — existing enum names (`JAVA_EXEC`, etc.) are valid strings |
| Flyway V6 | — | Drops CHECK constraint on `STEP_TYPE` | Allows any string value up to 50 chars in Oracle |
| Tests | H2, Flyway disabled, `create-drop` | Unchanged | Schema derived from entity — no migration files run |

## Entity Change (Done)

**File**: `domain/entity/JobStep.java`

```java
// Before
@Enumerated(EnumType.STRING)
@Column(name = "STEP_TYPE", nullable = false, length = 50)
private StepType stepType;

// After
@Column(name = "STEP_TYPE", nullable = false, length = 50)
private String stepType;
```

Backward-compatible setters retained so legacy code passing `StepType.JAVA_EXEC` still compiles:

```java
public void setStepType(StepType type) { this.stepType = type.name(); }
public void setStepType(String type)  { this.stepType = type; }
public StepType getStepTypeEnum()     { /* best-effort, returns null for unknown */ }
```

## Flyway Migration (Done)

**File**: `../../src/main/resources/db/migration/V6__relax_step_type_constraint.sql`

Drops any CHECK constraint on `JOB_STEP.STEP_TYPE` that references the column in its `search_condition`. Oracle-only PL/SQL block. Runs once at startup when Flyway is enabled (main profile). Does **not** run for tests (Flyway disabled, H2 `create-drop`).

## Why No Data Migration Is Needed

1. **No production data exists** — all testing uses H2 in-memory with schema created from JPA entities
2. **Existing step type values are valid strings** — `"JAVA_EXEC"`, `"SFTP"` etc. are legal under both the old enum and new string mapping
3. **Config blob is untouched** — `STEP_CONFIG` CLOB content remains the same JSON; executors parse it identically
4. **Dispatch is unchanged** — registry resolves by string, executors return the same type strings (`"JAVA_EXEC"` etc.)

## When This Matters

The V6 migration runs only when deploying to Oracle with Flyway enabled. Until then, tests exercise everything through H2 with no migrations involved.
