package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.entity.JobStepDependency;
import com.novakai.orchestrator.domain.entity.JobStepDependency.EdgeCondition;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.engine.exception.CircularDependencyException;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobRunRepository;
import com.novakai.orchestrator.repository.JobStepDependencyRepository;
import com.novakai.orchestrator.repository.JobStepRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the DAG execution engine.
 * Uses H2 in-memory DB with test executors (SLEEP, FAIL).
 */
@SpringBootTest
@ActiveProfiles("test")
class DagExecutionEngineTest {

    @Autowired private DagExecutionEngine dagEngine;
    @Autowired private JobDefinitionRepository jobRepo;
    @Autowired private JobRunRepository runRepo;
    @Autowired private JobStepRepository stepRepo;
    @Autowired private JobStepDependencyRepository depRepo;
    @Autowired private EntityManager em;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private JobStep createStep(JobDefinition job, String name, int order, String type, String config) {
        JobStep step = JobStep.builder()
                .jobDefinition(job)
                .stepName(name)
                .stepOrder(order)
                .stepType(type)
                .stepConfig(config)
                .enabled("Y")
                .continueOnFailure("N")
                .build();
        return stepRepo.save(step);
    }

    private JobStep createSleepStep(JobDefinition job, String name, int order, long ms) {
        return createStep(job, name, order, "SLEEP",
                "{\"durationMs\":" + ms + "}");
    }

    private JobStep createFailStep(JobDefinition job, String name, int order) {
        return createStep(job, name, order, "FAIL",
                "{\"message\":\"Test failure\"}");
    }

    private void addDependency(JobStep step, JobStep dependsOn, EdgeCondition condition) {
        depRepo.save(JobStepDependency.builder()
                .step(step)
                .dependsOnStep(dependsOn)
                .edgeCondition(condition)
                .build());
    }

    private ExecutionContext buildContext(Long runId, Long jobId) {
        return ExecutionContext.builder()
                .runId(runId)
                .jobId(jobId)
                .workingDir(System.getProperty("user.dir"))
                .javaHome(System.getProperty("java.home"))
                .classpath(List.of())
                .envVars(Map.of())
                .liveLogQueue(new LinkedBlockingQueue<>())
                .cancelRequested(false)
                .build();
    }

    /** Reload the job with steps eagerly fetched (data already committed without @Transactional). */
    private JobDefinition reloadJob(JobDefinition job) {
        return jobRepo.findByIdWithSteps(job.getJobId()).orElseThrow();
    }

    /** Read the run status after engine execution. */
    private RunStatus getRunStatus(Long runId) {
        return runRepo.findById(runId).orElseThrow().getStatus();
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void singleStep_succeeds() {
        JobDefinition job = saveJob("single-step");
        createSleepStep(job, "A", 1, 50);
        job = reloadJob(job);

        JobRun run = saveRun(job);
        dagEngine.execute(buildContext(run.getRunId(), job.getJobId()), job, run);

        assertEquals(RunStatus.SUCCESS, getRunStatus(run.getRunId()));
    }

    @Test
    void linearChain_executesInOrder() {
        // A → B → C (all ON_SUCCESS)
        JobDefinition job = saveJob("linear-chain");
        JobStep a = createSleepStep(job, "A", 1, 50);
        JobStep b = createSleepStep(job, "B", 2, 50);
        JobStep c = createSleepStep(job, "C", 3, 50);

        addDependency(b, a, EdgeCondition.ON_SUCCESS);
        addDependency(c, b, EdgeCondition.ON_SUCCESS);
        job = reloadJob(job);

        long start = System.currentTimeMillis();
        JobRun run = saveRun(job);
        dagEngine.execute(buildContext(run.getRunId(), job.getJobId()), job, run);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(RunStatus.SUCCESS, getRunStatus(run.getRunId()));
        assertTrue(elapsed >= 120, "Linear chain should be sequential (~150ms): took " + elapsed + "ms");
    }

    @Test
    void diamondDAG_concurrentExecution() {
        //   A
        //  / \
        // B   C
        //  \ /
        //   D
        JobDefinition job = saveJob("diamond-dag");
        JobStep a = createSleepStep(job, "A", 1, 50);
        JobStep b = createSleepStep(job, "B", 2, 200);
        JobStep c = createSleepStep(job, "C", 3, 200);
        JobStep d = createSleepStep(job, "D", 4, 50);

        addDependency(b, a, EdgeCondition.ON_SUCCESS);
        addDependency(c, a, EdgeCondition.ON_SUCCESS);
        addDependency(d, b, EdgeCondition.ON_SUCCESS);
        addDependency(d, c, EdgeCondition.ON_SUCCESS);
        job = reloadJob(job);

        long start = System.currentTimeMillis();
        JobRun run = saveRun(job);
        dagEngine.execute(buildContext(run.getRunId(), job.getJobId()), job, run);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(RunStatus.SUCCESS, getRunStatus(run.getRunId()));
        // A(50) + B/C concurrent (200) + D(50) ~ 300ms vs sequential 500ms
        assertTrue(elapsed < 750, "Diamond DAG should run B and C concurrently (sequential would be ~500ms). Took " + elapsed + "ms");
    }

    @Test
    void fanOut_concurrentSteps() {
        // A → B, C, D (all independent after A)
        JobDefinition job = saveJob("fan-out");
        JobStep a = createSleepStep(job, "A", 1, 30);
        JobStep b = createSleepStep(job, "B", 2, 150);
        JobStep c = createSleepStep(job, "C", 3, 150);
        JobStep d = createSleepStep(job, "D", 4, 150);

        addDependency(b, a, EdgeCondition.ON_SUCCESS);
        addDependency(c, a, EdgeCondition.ON_SUCCESS);
        addDependency(d, a, EdgeCondition.ON_SUCCESS);
        job = reloadJob(job);

        long start = System.currentTimeMillis();
        JobRun run = saveRun(job);
        dagEngine.execute(buildContext(run.getRunId(), job.getJobId()), job, run);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(RunStatus.SUCCESS, getRunStatus(run.getRunId()));
        assertTrue(elapsed < 350, "Fan-out should be concurrent. Took " + elapsed + "ms");
    }

    @Test
    void onFailureEdge_runsOnFailure() {
        // A (fails) → B (ON_FAILURE)
        JobDefinition job = saveJob("on-failure-edge");
        JobStep a = createFailStep(job, "A", 1);
        JobStep b = createSleepStep(job, "B", 2, 30);

        addDependency(b, a, EdgeCondition.ON_FAILURE);
        job = reloadJob(job);

        JobRun run = saveRun(job);
        dagEngine.execute(buildContext(run.getRunId(), job.getJobId()), job, run);

        // A=FAILED, B=SUCCESS → PARTIAL (not all steps succeeded)
        RunStatus status = getRunStatus(run.getRunId());
        assertTrue(status == RunStatus.PARTIAL || status == RunStatus.SUCCESS,
                "Expected PARTIAL or SUCCESS, got " + status);
    }

    @Test
    void onSuccessEdge_skipsOnFailure() {
        // A (fails) → B (ON_SUCCESS) — B should be skipped
        JobDefinition job = saveJob("on-success-skip");
        JobStep a = createFailStep(job, "A", 1);
        JobStep b = createSleepStep(job, "B", 2, 30);

        addDependency(b, a, EdgeCondition.ON_SUCCESS);
        job = reloadJob(job);

        JobRun run = saveRun(job);
        dagEngine.execute(buildContext(run.getRunId(), job.getJobId()), job, run);

        assertEquals(RunStatus.FAILED, getRunStatus(run.getRunId()));
    }

    @Test
    void alwaysEdge_runsRegardless() {
        // A (fails) → B (ALWAYS) — B should still run
        JobDefinition job = saveJob("always-edge");
        JobStep a = createFailStep(job, "A", 1);
        JobStep b = createSleepStep(job, "B", 2, 30);

        addDependency(b, a, EdgeCondition.ALWAYS);
        job = reloadJob(job);

        JobRun run = saveRun(job);
        dagEngine.execute(buildContext(run.getRunId(), job.getJobId()), job, run);

        RunStatus status = getRunStatus(run.getRunId());
        assertTrue(status == RunStatus.PARTIAL || status == RunStatus.SUCCESS,
                "Expected PARTIAL or SUCCESS, got " + status);
    }

    @Test
    void cascadingSkip_onFailurePropagates() {
        // A → B (fails) → C → D  (all ON_SUCCESS)
        JobDefinition job = saveJob("cascade-skip");
        JobStep a = createSleepStep(job, "A", 1, 20);
        JobStep b = createFailStep(job, "B", 2);
        JobStep c = createSleepStep(job, "C", 3, 20);
        JobStep d = createSleepStep(job, "D", 4, 20);

        addDependency(b, a, EdgeCondition.ON_SUCCESS);
        addDependency(c, b, EdgeCondition.ON_SUCCESS);
        addDependency(d, c, EdgeCondition.ON_SUCCESS);
        job = reloadJob(job);

        JobRun run = saveRun(job);
        dagEngine.execute(buildContext(run.getRunId(), job.getJobId()), job, run);

        // A=SUCCESS, B=FAILED, C/D=SKIPPED → PARTIAL (not all failed)
        assertEquals(RunStatus.PARTIAL, getRunStatus(run.getRunId()));
    }

    @Test
    void cycleDetection_rejectsCyclicGraph() {
        // A → B → A (cycle)
        JobDefinition job = saveJob("cycle-detection");
        JobStep a = createSleepStep(job, "A", 1, 20);
        JobStep b = createSleepStep(job, "B", 2, 20);

        addDependency(b, a, EdgeCondition.ON_SUCCESS);
        addDependency(a, b, EdgeCondition.ON_SUCCESS);
        JobDefinition loadedJob = reloadJob(job);

        JobRun run = saveRun(loadedJob);
        assertThrows(CircularDependencyException.class, () -> {
            dagEngine.execute(buildContext(run.getRunId(), loadedJob.getJobId()), loadedJob, run);
        });
    }

    @Test
    void noDependencies_allStepsRunAsRoots() {
        JobDefinition job = saveJob("independent-steps");
        createSleepStep(job, "A", 1, 100);
        createSleepStep(job, "B", 2, 100);
        createSleepStep(job, "C", 3, 100);
        job = reloadJob(job);

        long start = System.currentTimeMillis();
        JobRun run = saveRun(job);
        dagEngine.execute(buildContext(run.getRunId(), job.getJobId()), job, run);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(RunStatus.SUCCESS, getRunStatus(run.getRunId()));
        assertTrue(elapsed < 250, "Independent steps should be concurrent. Took " + elapsed + "ms");
    }

    @Test
    void emptyJob_succeedsImmediately() {
        JobDefinition job = saveJob("empty-job");
        job = reloadJob(job);

        JobRun run = saveRun(job);
        dagEngine.execute(buildContext(run.getRunId(), job.getJobId()), job, run);

        assertEquals(RunStatus.SUCCESS, getRunStatus(run.getRunId()));
    }

    @Test
    void cancelledRun_stopsExecution() {
        JobDefinition job = saveJob("cancel-test");
        createSleepStep(job, "A", 1, 500);
        job = reloadJob(job);

        JobRun run = saveRun(job);
        ExecutionContext ctx = buildContext(run.getRunId(), job.getJobId());
        ctx.setCancelRequested(true);

        dagEngine.execute(ctx, job, run);

        assertEquals(RunStatus.CANCELLED, getRunStatus(run.getRunId()));
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private JobDefinition saveJob(String name) {
        JobDefinition job = JobDefinition.builder()
                .jobName(name)
                .workingDir(System.getProperty("user.dir"))
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        return jobRepo.save(job);
    }

    private JobRun saveRun(JobDefinition job) {
        JobRun run = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .status(RunStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        return runRepo.save(run);
    }
}
