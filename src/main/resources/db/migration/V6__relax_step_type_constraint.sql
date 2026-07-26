-- Relax STEP_TYPE CHECK constraint to allow dynamically registered step types.
-- The column remains VARCHAR2(50) which provides a length guard; type validation is now enforced by the registry at runtime.

BEGIN
  FOR c IN (
    SELECT constraint_name
      FROM user_constraints
     WHERE table_name = 'JOB_STEP'
       AND search_condition LIKE '%STEP_TYPE%'
  ) LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE job_step DROP CONSTRAINT ' || c.constraint_name;
  END LOOP;
END;
/
