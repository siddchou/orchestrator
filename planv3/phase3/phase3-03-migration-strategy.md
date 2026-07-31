# Phase 3.3 — Migration Strategy (Verification)

## Migrations Already Deployed

Both V8 and V9 exist and have been run:

| Migration | File | Purpose | Status |
|-----------|------|--------|--------|
| V8 | `V8__add_step_dependencies.sql` | Creates JOB_STEP_DEPENDENCY table with FKs, unique constraint, indexes | Deployed |
| V9 | `V9__backfill_step_dependencies.sql` | Converts stepOrder chains to dependency edges, respects continueOnFailure → ALWAYS | Deployed |

## Schema Verification

The created table matches the design:
- `DEPENDENCY_ID` — surrogate key (NUMBER GENERATED ALWAYS AS IDENTITY)
- `STEP_ID` — downstream step (FK to JOB_STEP, ON DELETE CASCADE)
- `DEPENDS_ON_STEP_ID` — upstream step (FK to JOB_STEP)
- `EDGE_CONDITION` — VARCHAR2(20), CHECK constraint: ON_SUCCESS / ON_FAILURE / ALWAYS
- `UQ_STEP_DEP` — unique constraint on (STEP_ID, DEPENDS_ON_STEP_ID) prevents duplicate edges
- Indexes on both FK columns for DAG traversal queries

**Audit:** Schema is correct. No changes needed.

## Backfill Verification

The V9 PL/SQL block:
1. Iterates each job's steps ordered by stepOrder ascending
2. For each adjacent pair (step N, step N+1), inserts a dependency row
3. Step with lowest stepOrder gets no dependencies (root step)
4. Respects `continueOnFailure=Y` on the previous step → uses `ALWAYS` edge condition

**Audit:** Logic is correct for linear chains. All current jobs are linear, so this covers existing data.

### Verification Queries to Run

```sql
-- Check all multi-step jobs have dependencies
SELECT j.JOB_NAME,
       (SELECT COUNT(*) FROM JOB_STEP WHERE JOB_ID = j.JOB_ID) as step_count,
       (SELECT COUNT(*) FROM JOB_STEP s
        LEFT JOIN JOB_STEP_DEPENDENCY d ON s.STEP_ID = d.STEP_ID
        WHERE s.JOB_ID = j.JOB_ID AND d.DEPENDENCY_ID IS NULL) as root_count
FROM JOB_DEFINITION j
HAVING step_count > 1 AND root_count != 1;
-- Should return empty — every multi-step job has exactly one root

-- Check no orphan steps in multi-step jobs (steps with no deps and no dependents)
SELECT s.STEP_NAME, s.JOB_ID
FROM JOB_STEP s
WHERE NOT EXISTS (SELECT 1 FROM JOB_STEP_DEPENDENCY d WHERE d.STEP_ID = s.STEP_ID)
  AND NOT EXISTS (SELECT 1 FROM JOB_STEP_DEPENDENCY d WHERE d.DEPENDS_ON_STEP_ID = s.STEP_ID)
  AND (SELECT COUNT(*) FROM JOB_STEP WHERE JOB_ID = s.JOB_ID) > 1;
-- Should return empty

-- Check edge conditions match continueOnFailure mapping
SELECT ds.STEP_NAME as downstream, us.STEP_NAME as upstream, d.EDGE_CONDITION, us.CONTINUE_ON_FAILURE
FROM JOB_STEP_DEPENDENCY d
JOIN JOB_STEP ds ON d.STEP_ID = ds.STEP_ID
JOIN JOB_STEP us ON d.DEPENDS_ON_STEP_ID = us.STEP_ID
WHERE us.CONTINUE_ON_FAILURE = 'Y' AND d.EDGE_CONDITION != 'ALWAYS';
-- Should return empty — steps with continueOnFailure=Y should have ALWAYS edges to next step
```

## Rollback Strategy

### Database rollback (manual)
```sql
DELETE FROM JOB_STEP_DEPENDENCY;
DROP TABLE JOB_STEP_DEPENDENCY;
```

### Application-level rollback
The old sequential loop code in `JobExecutionOrchestrator` is retained. If the DAG engine has critical bugs, a feature flag (`orchestrator.engine.use-dag=false`) can revert to sequential execution. The dependency table rows are harmless if unused.

## Next Migration

No new migrations are needed for Phase 3 bug fixes. BUG-4 (CANCELLED status) may require:
- Adding `CANCELLED` to the `STEP_STATUS` CHECK constraint on `JOB_RUN_STEP` — **only if** the column uses a CHECK constraint with an explicit enum list. If it's a free-form VARCHAR2, no migration is needed.

Verify current constraint:
```sql
SELECT CONSTRAINT_NAME, SEARCH_CONDITION
FROM ALL_CONSTRAINTS
WHERE TABLE_NAME = 'JOB_RUN_STEP'
  AND CONSTRAINT_TYPE = 'C';
```

If a CHECK constraint exists on STEP_STATUS, create `V10__add_cancelled_step_status.sql`:
```sql
ALTER TABLE JOB_RUN_STEP
  DROP CONSTRAINT CK_RUN_STEP_STATUS;
ALTER TABLE JOB_RUN_STEP
  ADD CONSTRAINT CK_RUN_STEP_STATUS
  CHECK (STEP_STATUS IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED', 'CANCELLED'));
```
