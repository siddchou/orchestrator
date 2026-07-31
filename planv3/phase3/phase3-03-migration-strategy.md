<!-- FILE: phase3-03-migration-strategy.md -->
# Phase 3.3 — Migration Strategy

## V8 — Add Dependency Table

**File:** `src/main/resources/db/migration/V8__add_step_dependencies.sql`

```sql
-- ============================================================
-- V8: Add DAG dependency model for step execution ordering
-- Replaces linear stepOrder with explicit dependency edges
-- ============================================================

-- 1. Create the dependency join table
CREATE TABLE JOB_STEP_DEPENDENCY (
    DEPENDENCY_ID       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    STEP_ID             NUMBER NOT NULL,
    DEPENDS_ON_STEP_ID  NUMBER NOT NULL,
    EDGE_CONDITION      VARCHAR2(20) DEFAULT 'ON_SUCCESS'
                        CHECK (EDGE_CONDITION IN ('ON_SUCCESS', 'ON_FAILURE', 'ALWAYS')),
    CONSTRAINT FK_DEP_STEP       FOREIGN KEY (STEP_ID) REFERENCES JOB_STEP(STEP_ID) ON DELETE CASCADE,
    CONSTRAINT FK_DEP_ON_STEP    FOREIGN KEY (DEPENDS_ON_STEP_ID) REFERENCES JOB_STEP(STEP_ID),
    CONSTRAINT UQ_STEP_DEP       UNIQUE (STEP_ID, DEPENDS_ON_STEP_ID)
);

COMMENT ON TABLE JOB_STEP_DEPENDENCY IS 'DAG edges between steps: STEP_ID depends on DEPENDS_ON_STEP_ID';
COMMENT ON COLUMN JOB_STEP_DEPENDENCY.EDGE_CONDITION IS 'ON_SUCCESS (default), ON_FAILURE, or ALWAYS';

-- 2. Indexes for DAG traversal queries
CREATE INDEX IDX_DEP_TARGET ON JOB_STEP_DEPENDENCY(STEP_ID);
CREATE INDEX IDX_DEP_SOURCE ON JOB_STEP_DEPENDENCY(DEPENDS_ON_STEP_ID);
```

## V9 — Backfill Existing Jobs

**File:** `src/main/resources/db/migration/V9__backfill_step_dependencies.sql`

```sql
-- ============================================================
-- V9: Backfill dependencies from stepOrder for existing jobs
-- Converts linear chains into dependency chains (A→B→C→D)
-- Each step N depends on step N-1 with ON_SUCCESS condition
-- ============================================================

BEGIN
    FOR job_cursor IN (SELECT DISTINCT JOB_ID FROM JOB_STEP) LOOP
        -- Order steps by stepOrder within this job
        FOR step_cursor IN (
            SELECT STEP_ID, STEP_ORDER
            FROM JOB_STEP
            WHERE JOB_ID = job_cursor.JOB_ID
            ORDER BY STEP_ORDER ASC
        ) LOOP
            -- All steps except the first depend on the previous step
            FOR prev_cursor IN (
                SELECT STEP_ID
                FROM JOB_STEP
                WHERE JOB_ID = job_cursor.JOB_ID
                  AND STEP_ORDER = step_cursor.STEP_ORDER - 1
            ) LOOP
                INSERT INTO JOB_STEP_DEPENDENCY (STEP_ID, DEPENDS_ON_STEP_ID, EDGE_CONDITION)
                VALUES (step_cursor.STEP_ID, prev_cursor.STEP_ID, 'ON_SUCCESS');
            END LOOP;
        END LOOP;
    END LOOP;

    COMMIT;
END;
/
```

### Backfill Logic Explanation

- Iterates each job's steps in `stepOrder` ascending order
- Step with lowest `stepOrder` (typically 1) gets **no** dependency rows — it's a root step, starts immediately
- Every subsequent step gets one dependency row pointing to the previous step with `ON_SUCCESS` condition
- A 4-step job produces exactly 3 dependency rows: `(2→1), (3→2), (4→3)`

### Behavior Preservation

After backfill, existing jobs execute identically because:
1. The DAG engine sees a linear chain (each step depends on exactly one predecessor)
2. ON_SUCCESS condition means a failed step blocks all downstream steps — same as the current `continueOnFailure=N` default
3. Steps with `continueOnFailure=Y` currently allow the loop to continue; in the DAG model, this needs a separate handling strategy:

**Handling `continueOnFailure=Y` during backfill:**
- Options: (a) keep as ON_SUCCESS and accept that downstream steps get SKIPPED when such a step fails, or (b) convert to ALWAYS condition.
- **Decision:** Convert `continueOnFailure=Y` steps' incoming edges to the NEXT step to use `ALWAYS` condition. This preserves the existing behavior where the next step runs regardless of whether this one succeeded.

Updated backfill PL/SQL for this:

```sql
-- Modified backfill that respects continueOnFailure
BEGIN
    FOR job_cursor IN (SELECT DISTINCT JOB_ID FROM JOB_STEP) LOOP
        DECLARE
            prev_step_id NUMBER;
        BEGIN
            FOR step_cursor IN (
                SELECT STEP_ID, STEP_ORDER, CONTINUE_ON_FAILURE
                FROM JOB_STEP
                WHERE JOB_ID = job_cursor.JOB_ID
                ORDER BY STEP_ORDER ASC
            ) LOOP
                IF prev_step_id IS NOT NULL THEN
                    -- If the PREVIOUS step had continueOnFailure=Y, use ALWAYS
                    -- so this step runs regardless of previous step's result
                    DECLARE
                        prev_cof VARCHAR2(1);
                    BEGIN
                        SELECT CONTINUE_ON_FAILURE INTO prev_cof
                        FROM JOB_STEP WHERE STEP_ID = prev_step_id;

                        INSERT INTO JOB_STEP_DEPENDENCY (STEP_ID, DEPENDS_ON_STEP_ID, EDGE_CONDITION)
                        VALUES (
                            step_cursor.STEP_ID,
                            prev_step_id,
                            CASE WHEN prev_cof = 'Y' THEN 'ALWAYS' ELSE 'ON_SUCCESS' END
                        );
                    END;
                END IF;
                prev_step_id := step_cursor.STEP_ID;
            END LOOP;
        END;
    END LOOP;
    COMMIT;
END;
/
```

## Rollback Strategy

### Rollback SQL (manual, not a Flyway migration)

```sql
-- Undo V9 backfill
DELETE FROM JOB_STEP_DEPENDENCY;

-- Undo V8 table creation
DROP TABLE JOB_STEP_DEPENDENCY;
```

### Application-level rollback

If the DAG engine has bugs in production:
1. `JobLaunchService` can be configured to use the old `JobExecutionOrchestrator` sequentially via a feature flag property (`orchestrator.engine.use-dag=false`)
2. The old sequential loop code is retained (not deleted) until Phase 3 is stable for one release cycle
3. Rolling back to the previous application jar restores sequential execution; the dependency table rows are harmless if unused

## Post-Migration Verification

After migration, verify with these queries:

```sql
-- Check all jobs have at least one root step (no dependencies)
SELECT j.JOB_NAME, COUNT(*) as dep_count
FROM JOB_STEP s
JOIN JOB_DEFINITION j ON s.JOB_ID = j.JOB_ID
LEFT JOIN JOB_STEP_DEPENDENCY d ON s.STEP_ID = d.STEP_ID
GROUP BY j.JOB_NAME
HAVING COUNT(*) = 0;
-- Should only show single-step jobs

-- Check no step is both a root and has dependents (would indicate orphan in multi-step job)
SELECT s.STEP_NAME, s.JOB_ID
FROM JOB_STEP s
WHERE NOT EXISTS (SELECT 1 FROM JOB_STEP_DEPENDENCY d WHERE d.STEP_ID = s.STEP_ID)
  AND EXISTS (SELECT 1 FROM JOB_STEP_DEPENDENCY d WHERE d.DEPENDS_ON_STEP_ID = s.STEP_ID)
  AND (SELECT COUNT(*) FROM JOB_STEP WHERE JOB_ID = s.JOB_ID) > 1;
```

## Migration Risk Assessment

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Backfill creates wrong edges for complex jobs | Low | All current jobs are linear chains; backfill is a simple N-1 pattern |
| PL/SQL block fails mid-migration on large dataset | Low | Wrapped in transaction; Oracle handles DML rollback on error |
| Application deployed before migration completes | Medium | Flyway runs at startup; application won't start if migration fails |
| Old sequential code path needed for rollback | — | Retained with feature flag, not deleted |
