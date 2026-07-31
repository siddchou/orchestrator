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
