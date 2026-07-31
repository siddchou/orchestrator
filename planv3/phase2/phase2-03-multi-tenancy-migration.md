# Phase 2 — Multi-Tenancy Migration Plan

## Current State: V7 Complete

The database migration for multi-tenancy **is already implemented** in [`V7__add_multi_tenancy.sql`](src/main/resources/db/migration/V7__add_multi_tenancy.sql).

### What V7 Does

```sql
-- 1. Create TEAM table
CREATE TABLE TEAM (
    ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    NAME VARCHAR(255) NOT NULL UNIQUE,
    DESCRIPTION VARCHAR(1000),
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. Create USER_TEAM join table with role check constraint
CREATE TABLE USER_TEAM (
    USER_ID BIGINT NOT NULL REFERENCES APP_USER(ID),
    TEAM_ID BIGINT NOT NULL REFERENCES TEAM(ID),
    ROLE VARCHAR(20) NOT NULL CHECK (ROLE IN ('ADMIN', 'MEMBER', 'VIEWER')),
    PRIMARY KEY (USER_ID, TEAM_ID)
);

-- 3. Add nullable TEAM_ID FK to JOB_DEFINITION
ALTER TABLE JOB_DEFINITION ADD COLUMN TEAM_ID BIGINT REFERENCES TEAM(ID);

-- 4. Seed "Default" team
INSERT INTO TEAM (NAME, DESCRIPTION) VALUES ('Default', 'Default team for existing data');

-- 5. Backfill existing jobs → Default team
UPDATE JOB_DEFINITION SET TEAM_ID = (SELECT ID FROM TEAM WHERE NAME = 'Default');

-- 6. Backfill existing users → Default team as ADMIN
INSERT INTO USER_TEAM (USER_ID, TEAM_ID, ROLE)
SELECT u.ID, t.ID, 'ADMIN' FROM APP_USER u CROSS JOIN TEAM t WHERE t.NAME = 'Default';

-- 7. Make TEAM_ID NOT NULL after backfill
ALTER TABLE JOB_DEFINITION MODIFY COLUMN TEAM_ID BIGINT NOT NULL;
```

### Subsequent Migrations (V8-V10)

| Version | Purpose | Multi-Tenancy Relevance |
|---------|---------|------------------------|
| V8 | Add step dependencies | Unrelated — DAG support |
| V9 | Backfill step dependencies | Unrelated — DAG support |
| V10 | Job definition versioning | Unrelated — version tracking |

**Next free migration: V11**

---

## Remaining Work

### 1. Server-Side Team Scoping (backend)

The **database schema supports multi-tenancy**, but the critical question is whether the **application layer enforces it**. The following needs verification:

| Area | Question | Action |
|------|----------|--------|
| **JWT claims** | Does the JWT contain a `teamId` claim? Or is team context derived from session/request header? | Check `JwtService.java` and `AuthInterceptor.java` |
| **Job API** | Do `GET /api/jobs`, `POST /api/jobs`, etc. filter by team? | Check `JobController.java` service layer calls |
| **Run API** | Are runs scoped to the job's team implicitly, or is there explicit filtering? | Check `RunController.java` |
| **Credential API** | Are credentials shared across teams or per-team? | Check `CredentialController.java` |
| **Audit log** | Does audit logging capture team context? | Check `AuditLogService.java` |

### Likely Implementation Pattern

Based on the frontend code, the expected flow is:

1. User logs in → JWT contains user identity + role
2. User selects a team via TeamSwitcher → `POST /api/teams/active/{id}`
3. Subsequent requests include team context (via header or refreshed JWT)
4. Backend filters all queries by active team ID

**If the backend currently lacks this scoping**, the work involves:
- Adding `teamId` to security context (request interceptor)
- Updating repository methods to accept and filter by `teamId`
- Adding admin override for cross-team visibility
- Writing integration tests with multi-team data

### 2. Additional Tables That May Need Scoping

| Table | Needs TEAM_ID? | Rationale |
|-------|---------------|-----------|
| `JOB_DEFINITION` | Yes — done in V7 | Jobs are team-owned |
| `JOB_STEP` | Implicit via FK to JOB_DEFINITION | No direct column needed |
| `JOB_ENV_VAR` | Implicit via FK to JOB_DEFINITION | Inherited from job |
| `JOB_RUN` | Implicit via FK to JOB_DEFINITION | Runs belong to jobs |
| `JOB_RUN_STEP` | Implicit via FK to JOB_RUN | Steps belong to runs |
| `JOB_SCHEDULE` | Implicit via FK to JOB_DEFINITION | Schedules belong to jobs |
| `JOB_CREDENTIAL` | **Maybe** — depends on whether credentials are shared or per-team | Design decision needed |
| `AUDIT_LOG` | **Should** — for team-scoped audit queries | Would need new column, no backfill possible for historical data |
| `APP_USER` | No — users are global, teams are in USER_TEAM | Cross-cutting entity |

### 3. Migration V11 (if needed)

If audit log scoping is desired:

```sql
-- V11__scope_audit_log_to_team.sql
ALTER TABLE AUDIT_LOG ADD COLUMN TEAM_ID BIGINT REFERENCES TEAM(ID);
-- No backfill — historical entries remain NULL (pre-multi-tenancy era)
CREATE INDEX IDX_AUDIT_TEAM ON AUDIT_LOG(TEAM_ID);
```

---

## Frontend Multi-Tenancy Status

| Component | Status | Notes |
|-----------|--------|-------|
| TeamService API calls | Done | `listMyTeams()`, `setActiveTeam()`, `getActiveTeam()` |
| TeamSwitcherComponent | Done | Dropdown with form guard, retry, cache fallback |
| AuthUser model update | Done | Includes `teams` and `activeTeamId` |
| Job list scoping | **Verify** | Does job list API call include team filter? |
| Run list scoping | **Verify** | Inherited from job team or explicit? |

---

## Risks

1. **Credential sharing model unclear.** If credentials are global, a team could reference another team's credential ID via SECRET_REF. If per-team, cross-team credential reuse is impossible. Decision needed before implementation.
2. **Admin visibility.** Admin users likely need to see all teams' data for troubleshooting. Role-based override in repository layer required.
3. **Migration safety.** V7 backfill is irreversible once `TEAM_ID NOT NULL` is enforced. Ensure rollback plan exists if issues arise (drop FK, delete columns).
