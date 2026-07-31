-- Add unique constraint on (JOB_ID, STEP_NAME) for defense-in-depth.
-- Application-level validation already rejects duplicates, but the DB constraint
-- prevents corruption from direct inserts or race conditions.

ALTER TABLE JOB_STEP ADD CONSTRAINT UQ_STEP_NAME UNIQUE (JOB_ID, STEP_NAME);
