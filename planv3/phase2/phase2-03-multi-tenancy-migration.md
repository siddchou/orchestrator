# Phase 2 — Multi-Tenancy Migration Plan

## Overview

Adds team-based scoping to all job/run data so multiple teams can share the same database instance without seeing each other's resources. Uses a TEAM table, USER_TEAM join table, and nullable FK on JOB_DEFINITION with default-team backfill for existing data.

**Migration version:** `V7__add_multi_tenancy.sql`
**Database:** Oracle (VARCHAR2, NUMBER GENERATED ALWAYS AS IDENTITY)

---

## Forward Migration — V7__add_multi_tenancy.sql

```sql
-- ============================================================
-- V7: Add multi-tenancy support (TEAM, USER_TEAM tables + FK)
-- ============================================================

-- 1. Create TEAM table
CREATE TABLE TEAM (
    TEAM_ID       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    TEAM_NAME     VARCHAR2(100) NOT NULL UNIQUE,
    CREATED_AT    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE TEAM IS 'Teams for multi-tenant job scoping';

-- 2. Create USER_TEAM join table (many-to-many with role per team)
CREATE TABLE USER_TEAM (
    USER_ID       NUMBER NOT NULL REFERENCES APP_USER(USER_ID),
    TEAM_ID       NUMBER NOT NULL REFERENCES TEAM(TEAM_ID),
    ROLE          VARCHAR2(30) DEFAULT 'MEMBER' CHECK (ROLE IN ('ADMIN', 'MEMBER')),
    CREATED_AT    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (USER_ID, TEAM_ID)
);

COMMENT ON TABLE USER_TEAM IS 'Maps users to teams with per-team role';

-- 3. Add nullable TEAM_ID FK to JOB_DEFINITION
ALTER TABLE JOB_DEFINITION ADD (TEAM_ID NUMBER REFERENCES TEAM(TEAM_ID));

COMMENT ON COLUMN JOB_DEFINITION.TEAM_ID IS 'Team that owns this job definition';

-- 4. Seed default team for backward compatibility
INSERT INTO TEAM (TEAM_NAME) VALUES ('Default');

-- 5. Backfill: assign all existing jobs to Default team
UPDATE JOB_DEFINITION SET TEAM_ID = (SELECT TEAM_ID FROM TEAM WHERE TEAM_NAME = 'Default')
WHERE TEAM_ID IS NULL;

-- 6. Make TEAM_ID non-null after backfill is complete
ALTER TABLE JOB_DEFINITION MODIFY (TEAM_ID NOT NULL);

-- 7. Seed: assign all existing users to Default team as ADMIN
INSERT INTO USER_TEAM (USER_ID, TEAM_ID, ROLE)
SELECT u.USER_ID, t.TEAM_ID, 'ADMIN'
FROM APP_USER u
CROSS JOIN (SELECT TEAM_ID FROM TEAM WHERE TEAM_NAME = 'Default') t
WHERE NOT EXISTS (
    SELECT 1 FROM USER_TEAM ut
    WHERE ut.USER_ID = u.USER_ID AND ut.TEAM_ID = t.TEAM_ID
);

-- 8. Create indexes for query performance
CREATE INDEX IDX_JOB_DEF_TEAM ON JOB_DEFINITION(TEAM_ID);
```

---

## Rollback Migration — V7__add_multi_tenancy.sql.rollback

```sql
-- Rollback: Remove multi-tenancy (for emergency rollback only)
DROP INDEX IDX_JOB_DEF_TEAM;
DELETE FROM USER_TEAM;
ALTER TABLE JOB_DEFINITION DROP COLUMN TEAM_ID;
DROP TABLE USER_TEAM;
DROP TABLE TEAM;
```

**Note:** This rollback is destructive — it deletes all team assignments. Only use for pre-production rollbacks. In production, prefer a forward-fix migration (V8) over rollback.

---

## Backward Compatibility Strategy

### During Migration Window (minutes between V7 step 5 and step 6)

1. **Step 4-5** creates the Default team and assigns all existing jobs. This runs inside one transaction — no partial state is visible to application code.
2. **Step 6** makes TEAM_ID non-null immediately after backfill. If any job somehow missed the UPDATE (shouldn't happen in same transaction), ALTER TABLE would fail atomically — migration rolls back entirely.

### Application Code During Transition

| Scenario | Handling |
|----------|----------|
| New code deploys before migration runs | Backend checks `TEAM_ID IS NULL` → treats as belonging to all teams (backward compat mode). A simple `@PostLoad` or query-time logic: `WHERE team_id = :teamId OR team_id IS NULL`. |
| Migration runs before new code deploys | Old code sees non-null TEAM_ID column but ignores it — no FK violation since Default team exists. Jobs remain accessible because old queries don't filter by team. |
| Both deploy together (preferred) | Clean cutover: all jobs have a team, all queries filter by team. No compat mode needed. |

### Recommended Deployment Order

1. Run Flyway migration V7 on database
2. Deploy backend with team-scoped queries + backward compat null check
3. Deploy frontend with team switcher

This order ensures the database is ready before any code references it, and the backward-compat null check handles the brief window if deployment is staggered.

---

## Data Model After Migration

```
APP_USER (1) ───< USER_TEAM >─── (N) TEAM (1) ───< JOB_DEFINITION
       │                                    │
       │                                  UPDATED_AT
       │                                    │
       └── role: ADMIN/VIEWER              CREATED_AT
           (global, from V4)               TEAM_ID → FK

USER_TEAM.role: ADMIN/MEMBER  (per-team role, separate from global role)
JOB_DEFINITION.TEAM_ID: NOT NULL FK → TEAM(TEAM_ID)
```

### Query Patterns

**List jobs for active team:**
```sql
SELECT * FROM JOB_DEFINITION WHERE TEAM_ID = :activeTeamId
```

**ADMIN bypasses team filter (global admin only):**
```java
if ("ADMIN".equals(user.getRole())) {
    // no team filter — see all jobs
} else {
    query.and("teamId", activeTeamId);
}
```

**List user's teams:**
```sql
SELECT t.TEAM_ID, t.TEAM_NAME, ut.ROLE
FROM TEAM t
JOIN USER_TEAM ut ON t.TEAM_ID = ut.TEAM_ID
WHERE ut.USER_ID = :userId
ORDER BY t.TEAM_NAME
```

---

## API Endpoints for Team Management

| Method | Path | Auth Required | Description |
|--------|------|---------------|-------------|
| GET | `/api/teams/my-teams` | Yes | Returns `List<TeamSummary>` with `{teamId, teamName, role}` for current user |
| POST | `/api/teams/active/{teamId}` | Yes | Sets active team in HTTP session. Validates user is a member of the team. |
| GET | `/api/teams/active` | Yes | Returns `{teamId, teamName}` of the currently active team from session |

**Session behavior:** On login, if the user has exactly one team, it becomes their active team automatically. If they have multiple teams, `GET /api/teams/active` returns null until they select one via the frontend switcher. The active team ID is sent as an HTTP header (`X-Team-Id`) on subsequent requests for defense in depth (even though server-side session filtering is the primary guard).
