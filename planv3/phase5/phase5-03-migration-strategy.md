<!-- FILE: phase5-03-migration-strategy-strategy.md -->
# Phase 5 — Migration Strategy

## V12 Flyway Migration

**File:** `src/main/resources/db/migration/V12__create_notification_tables.sql`

```sql
-- Notification subscriptions: per-job notification channel configurations
CREATE TABLE NOTIFICATION_SUBSCRIPTION (
    id                NUMBER(19)            PRIMARY KEY,
    job_definition_id NUMBER(19)            NOT NULL CONSTRAINT fk_ns_job_def REFERENCES JOB_DEFINITION(job_id),
    channel_type      VARCHAR2(30 CHAR)     NOT NULL,
    channel_config    CLOB                  NOT NULL,  -- JSON: webhook URL, recipients, headers, etc.
    events            VARCHAR2(100 CHAR)    NOT NULL,  -- comma-separated RunStatus values: SUCCESS,FAILED,CANCELLED
    created_at        TIMESTAMP             DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at        TIMESTAMP             DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX idx_ns_job_id ON NOTIFICATION_SUBSCRIPTION(job_definition_id);
CREATE INDEX idx_ns_channel ON NOTIFICATION_SUBSCRIPTION(channel_type);

COMMENT ON TABLE NOTIFICATION_SUBSCRIPTION IS 'Per-job notification subscriptions with channel config';
COMMENT ON COLUMN NOTIFICATION_SUBSCRIPTION.events IS 'Comma-separated RunStatus values triggering this subscription';

-- Notification delivery log: tracks each dispatch attempt for observability
CREATE TABLE NOTIFICATION_DELIVERY_LOG (
    id                NUMBER(19)            PRIMARY KEY,
    subscription_id   NUMBER(19)            NOT NULL CONSTRAINT fk_ndl_sub REFERENCES NOTIFICATION_SUBSCRIPTION(id),
    run_id            NUMBER(19)            NOT NULL CONSTRAINT fk_ndl_run REFERENCES JOB_RUN(run_id),
    status            VARCHAR2(20 CHAR)     NOT NULL,  -- PENDING, RETRYING, SENT, FAILED
    attempt_count     NUMBER(10)            DEFAULT 0  NOT NULL,
    last_error        VARCHAR2(4000 CHAR),
    sent_at           TIMESTAMP,
    created_at        TIMESTAMP             DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX idx_ndl_sub ON NOTIFICATION_DELIVERY_LOG(subscription_id);
CREATE INDEX idx_ndl_run ON NOTIFICATION_DELIVERY_LOG(run_id);

COMMENT ON TABLE NOTIFICATION_DELIVERY_LOG IS 'Delivery attempt log for notification subscriptions';
COMMENT ON COLUMN NOTIFICATION_DELIVERY_LOG.status IS 'PENDING | RETRYING | SENT | FAILED';

-- Sequence objects (matching existing project convention)
CREATE SEQUENCE notification_subscription_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE notification_delivery_log_seq   START WITH 1 INCREMENT BY 1 NOCACHE;
```

### Design decisions

- **`events` as comma-separated string** rather than a junction table: Keeps the schema simple (2 tables, not 3). The dispatcher splits on commas and checks containment. Matches the plan's original design. Values are validated against `RunStatus` enum in the service layer before save.
- **`channel_config` as CLOB:** Consistent with existing `JOB_STEP.config_json` pattern. JSON is validated at the API layer, not the database layer.
- **Foreign keys to JOB_DEFINITION and JOB_RUN:** Enforces referential integrity. Deleting a job cascades to subscriptions (see below).
- **Oracle-specific DDL:** Uses `NUMBER(19)` for BIGINT equivalents, `VARCHAR2` with char semantics, `CLOB`, `SYSTIMESTAMP`. Flyway handles Oracle; H2 test migrations can use compatible syntax if needed.

## Rollback SQL

```sql
DROP SEQUENCE notification_delivery_log_seq;
DROP SEQUENCE notification_subscription_seq;
DROP TABLE NOTIFICATION_DELIVERY_LOG;
DROP TABLE NOTIFICATION_SUBSCRIPTION;
```

## Additive-only confirmation

This migration is **purely additive**:
- Creates 2 new tables, 2 new sequences
- Does NOT alter any existing table
- Does NOT modify existing data
- No foreign keys FROM existing tables TO the new tables (only the reverse)
- Existing application functionality is unaffected if no subscriptions are created

## H2 test compatibility

Since tests use H2 in Oracle-compatible mode (the project already uses H2 for testing with Oracle as production), ensure the test profile either:
1. Skips Flyway migrations entirely (common pattern — use `spring.flyway.enabled=false` in test profile and use schema.sql), OR
2. Uses H2-compatible DDL in a separate test migration script

**[ASSUMED]** The project currently uses approach 1 (Flyway disabled in test, schema from JPA `ddl-auto` or `schema.sql`). Verify by checking `application-test.yml`. If Flyway runs against H2, the V12 migration needs an H2-compatible variant.

## Cascade behavior

When a job definition is deleted:
- **NOTIFICATION_SUBSCRIPTION rows should be cascade-deleted** (the subscription is meaningless without the job)
- **NOTIFICATION_DELIVERY_LOG rows:** Two options:
  - Option A: Also cascade-delete (clean slate, no orphaned logs)
  - Option B: `ON DELETE SET NULL` on `subscription_id` (preserve audit trail)

**Recommendation:** Option A for both. Add `ON DELETE CASCADE` to the foreign keys if Oracle DDL supports it in this context, or handle cascade in JPA entity mappings (`@OneToMany(cascade = REMOVE)`).
