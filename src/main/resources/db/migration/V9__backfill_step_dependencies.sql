-- ============================================================
-- V9: Backfill dependencies from stepOrder for existing jobs
-- Converts linear chains into dependency chains (A→B→C→D)
-- Each step N depends on step N-1 with ON_SUCCESS condition
-- If the previous step had continueOnFailure=Y, use ALWAYS
-- ============================================================

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
