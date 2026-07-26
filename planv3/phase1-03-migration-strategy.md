# Phase 1 — Migration Strategy

## Current State (Confirmed from V1 Migration)

The `JOB_STEP` table already stores config as a generic CLOB JSON blob (`STEP_CONFIG`). The only blocking constraint is the CHECK on `STEP_TYPE`:

```sql
-- From V1__create_job_definition.sql:17-18
STEP_TYPE VARCHAR2(50) NOT NULL
    CHECK (STEP_TYPE IN ('ENV_SETUP','LOG_CLEANUP','JAVA_EXEC','SFTP','ARCHIVE')),
```

The JPA entity maps this to `StepType` enum via `@Enumerated(EnumType.STRING)` — meaning the column stores the string name of the enum value.

## What Needs to Change

1. **DB**: Remove or relax the CHECK constraint so new step type strings can be inserted.
2. **Java**: The entity's `stepType` field changes from `StepType` (enum) to `String`. This is a breaking change for the JPA mapping but backward compatible at runtime because existing rows contain values that match the old enum names.

---

## Flyway Migration: V{N+1}__relax_step_type_constraint.sql

> **Pre-check (gap-analysis Fix #8)**: code review confirmed V1 (`job_definition`) and V3 (`job_credential`) migrations exist but never enumerated the full `src/main/resources/db/migration/` directory — V2, V4, and V5 are unaccounted for. **Do not assume V6 is the next free version number.** Before running this migration, list the directory and confirm the actual highest existing version, then name the file accordingly. The filename below uses `V6` as a placeholder only.

```sql
-- ============================================================
-- V6: Relax STEP_TYPE CHECK constraint to allow pluggable types
-- ============================================================
-- Rationale: The current CHECK constraint limits STEP_TYPE to 5 hardcoded
-- enum values. Removing it allows new step types to be registered at runtime
-- via the StepExecutor SPI without DB migrations.

-- Oracle does not support DROP CONSTRAINT by name when the constraint was
-- created inline (no explicit name). Find and drop the generated constraint:

-- Step 1: Drop the existing CHECK constraint.
-- Query the constraint name first (for idempotency in scripts):
DECLARE
    v_constraint_name VARCHAR2(128);
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM all_constraints
    WHERE table_name = 'JOB_STEP'
      AND search_condition IS NOT NULL
      AND search_condition LIKE '%STEP_TYPE%';

    IF v_count > 0 THEN
        SELECT constraint_name INTO v_constraint_name
        FROM all_constraints
        WHERE table_name = 'JOB_STEP'
          AND search_condition IS NOT NULL
          AND search_condition LIKE '%STEP_TYPE%'
        FETCH FIRST 1 ROWS ONLY;

        EXECUTE IMMEDIATE 'ALTER TABLE JOB_STEP DROP CONSTRAINT ' || v_constraint_name;
    END IF;
END;
/

-- Step 2: Optionally add a softer validation constraint.
-- This allows any non-null value up to 50 chars but rejects obviously bad input.
-- (On Oracle, CHECK constraints can call functions — but keep it simple.)
ALTER TABLE JOB_STEP
    ADD CONSTRAINT CHK_STEP_TYPE_FORMAT
    CHECK (STEP_TYPE IS NOT NULL AND LENGTH(STEP_TYPE) BETWEEN 1 AND 50);

-- Step 3: Add an index for lookup performance.
-- The registry resolves by type string; an index helps the "list all registered types" query.
CREATE INDEX IDX_JOB_STEP_TYPE ON JOB_STEP(STEP_TYPE);
```

---

## Rollback SQL

```sql
-- ============================================================
-- ROLLBACK V6: Restore original CHECK constraint
-- ============================================================
-- Only safe if no new step type values exist in the table.
-- Fails with ORA-02436 if rows violate the constraint — which is correct behavior.

DECLARE
    v_constraint_name VARCHAR2(128);
    v_count NUMBER;
BEGIN
    -- Drop the soft constraint we added
    SELECT COUNT(*) INTO v_count
    FROM all_constraints
    WHERE table_name = 'JOB_STEP'
      AND constraint_name = 'CHK_STEP_TYPE_FORMAT';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE JOB_STEP DROP CONSTRAINT CHK_STEP_TYPE_FORMAT';
    END IF;
END;
/

-- Drop the index we added
DROP INDEX IDX_JOB_STEP_TYPE;

-- Restore the original hard constraint
ALTER TABLE JOB_STEP
    ADD CONSTRAINT CHK_STEP_TYPE_ENUM
    CHECK (STEP_TYPE IN ('ENV_SETUP','LOG_CLEANUP','JAVA_EXEC','SFTP','ARCHIVE'));
```

---

## JPA Entity Change

**File**: `domain/entity/JobStep.java`

Current:
```java
@Enumerated(EnumType.STRING)
@Column(name = "STEP_TYPE", nullable = false, length = 50)
private StepType stepType;
```

After migration:
```java
@Column(name = "STEP_TYPE", nullable = false, length = 50)
private String stepType;
```

The `@Enumerated` annotation is removed. The field becomes a plain String. Existing rows contain values like `"JAVA_EXEC"` which the registry resolves via `get("JAVA_EXEC")`.

**Backward compat shim — corrected (see `phase1-07-gap-analysis-and-fixes.md`, Fix #1)**:

> An earlier draft of this section proposed `@Convert(converter = StepTypeConverter.class)` on a field declared `private String stepType`, with the converter typed as `AttributeConverter<StepType, String>`. That's a JPA type mismatch — `@Convert` requires the annotated field's Java type to match the converter's first type parameter (here `StepType`, not `String`), so it doesn't compile, and even fixed at the type level, JPA would reject the mismatch at bootstrap. Since the plan's own conclusion is "the orchestrator reads the raw string, not the enum, for dispatch," a converter isn't actually needed at all — dropped in favor of the simpler design below.

`StepType` enum is kept, unchanged, purely so legacy call sites passing `StepType.JAVA_EXEC` still compile via an overload:

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

No `AttributeConverter`, no `@Enumerated`, no type mismatch. When `getStepTypeEnum()` returns `null` for a step type not in the enum, the registry still resolves it via `getStepType()`'s raw string. The orchestrator reads the raw string (not the enum) for dispatch. This is now tracked as its own task — see Task 7 in `phase1-02-task-breakdown.md`.

---

## How Existing Job Definitions Keep Running Unmodified

1. **DB level**: Existing rows have STEP_TYPE values (`'JAVA_EXEC'`, `'SFTP'`, etc.) that are valid under both the old and new constraint. No data migration needed — the values don't change.
2. **Java level**: The migrated executor for each type returns `getType()` as the same string (e.g., `"JAVA_EXEC"`). The registry maps this string to the executor bean. Dispatch is identical: string → executor → execute().
3. **Config level**: `STEP_CONFIG` CLOB content is unchanged. Each executor still parses its own config record from JSON. The schema contract is additive — executors declare what they expect, but parsing logic remains in the executor during this phase.
4. **Orchestrator level**: `JobExecutionOrchestrator.executeStep()` changes from `executorFactory.resolve(step.getStepType())` (enum) to `registry.get(step.getStepType())` (String). The string value is the same, so resolution succeeds identically.

**Verification criterion**: After migration, run every existing job definition at least once. Each completes with the same status and log output as pre-migration. The regression test suite (Task 15) automates this for the test profiles.
