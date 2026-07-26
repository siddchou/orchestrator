package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobRunRepository;
import com.novakai.orchestrator.repository.JobStepRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that runs a job with multiple step types: ENV_SETUP, SHELL_EXEC.
 * Verifies the orchestrator dispatches each step to the correct executor via SPI registry.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MixedExecutorIntegrationTest {

    @Autowired
    private JobExecutionOrchestrator orchestrator;

    @Autowired
    private JobDefinitionRepository jobRepo;

    @Autowired
    private JobRunRepository runRepo;

    @Autowired
    private JobStepRepository stepRepo;

    @Autowired
    private EntityManager entityManager;

    private ExecutionContext buildContext(Long runId, Long jobId) {
        return ExecutionContext.builder()
                .runId(runId)
                .jobId(jobId)
                .workingDir(System.getProperty("user.dir"))
                .javaHome(extractJavaHome())
                .classpath(List.of())
                .envVars(System.getenv())
                .liveLogQueue(new java.util.concurrent.LinkedBlockingQueue<>())
                .cancelRequested(false)
                .build();
    }

    @Test
    void execute_multi_step_job_with_different_executors() throws Exception {
        JobDefinition job = JobDefinition.builder()
                .jobName("mixed-executor-job")
                .workingDir(System.getProperty("user.dir"))
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        job = jobRepo.save(job);

        // Step 1: ENV_SETUP
        stepRepo.save(JobStep.builder()
                .jobDefinition(job)
                .stepName("setup-env")
                .stepOrder(1)
                .stepType("ENV_SETUP")
                .stepConfig("{\"javaHome\":\"" + extractJavaHome() + "\",\"classpathEntries\":[],\"extraEnvVars\":{\"INTEGRATION_TEST\":\"true\"}}")
                .enabled("Y")
                .continueOnFailure("N")
                .build());

        // Step 2: SHELL_EXEC - echo a message
        stepRepo.save(JobStep.builder()
                .jobDefinition(job)
                .stepName("shell-command")
                .stepOrder(2)
                .stepType("SHELL_EXEC")
                .stepConfig("{\"command\":\"echo hello from shell exec\"}")
                .enabled("Y")
                .continueOnFailure("N")
                .build());

        // Flush and clear so findByIdWithSteps sees the new steps
        stepRepo.flush();
        entityManager.clear();

        // Reload job with steps
        job = jobRepo.findByIdWithSteps(job.getJobId()).orElseThrow();
        assertEquals(2, job.getSteps().size());

        // Create run and execute
        JobRun run = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .status(RunStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        run = runRepo.save(run);

        ExecutionContext ctx = buildContext(run.getRunId(), job.getJobId());
        orchestrator.execute(ctx, job, run);

        // Verify all steps completed successfully
        runRepo.flush();
        entityManager.clear();
        run = runRepo.findById(run.getRunId()).orElseThrow();
        assertEquals(RunStatus.SUCCESS, run.getStatus());
        assertNotNull(run.getStartedAt());
        assertNotNull(run.getEndedAt());
    }

    @Test
    void execute_shell_exec_then_env_setup_order() throws Exception {
        JobDefinition job = JobDefinition.builder()
                .jobName("order-test-job")
                .workingDir(System.getProperty("user.dir"))
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        job = jobRepo.save(job);

        // Step 1: SHELL_EXEC first
        stepRepo.save(JobStep.builder()
                .jobDefinition(job)
                .stepName("shell-first")
                .stepOrder(1)
                .stepType("SHELL_EXEC")
                .stepConfig("{\"command\":\"echo step one\"}")
                .enabled("Y")
                .continueOnFailure("N")
                .build());

        // Step 2: ENV_SETUP second
        stepRepo.save(JobStep.builder()
                .jobDefinition(job)
                .stepName("env-second")
                .stepOrder(2)
                .stepType("ENV_SETUP")
                .stepConfig("{\"javaHome\":\"" + extractJavaHome() + "\",\"classpathEntries\":[],\"extraEnvVars\":{}}")
                .enabled("Y")
                .continueOnFailure("N")
                .build());

        stepRepo.flush();
        entityManager.clear();
        job = jobRepo.findByIdWithSteps(job.getJobId()).orElseThrow();

        JobRun run = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .status(RunStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        run = runRepo.save(run);

        ExecutionContext ctx = buildContext(run.getRunId(), job.getJobId());
        orchestrator.execute(ctx, job, run);

        runRepo.flush();
        entityManager.clear();
        run = runRepo.findById(run.getRunId()).orElseThrow();
        assertEquals(RunStatus.SUCCESS, run.getStatus());
    }

    @Test
    void execute_continue_on_failure_skips_failed_step() throws Exception {
        JobDefinition job = JobDefinition.builder()
                .jobName("continue-failure-job")
                .workingDir(System.getProperty("user.dir"))
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        job = jobRepo.save(job);

        // Step 1: ENV_SETUP (succeeds)
        stepRepo.save(JobStep.builder()
                .jobDefinition(job)
                .stepName("setup")
                .stepOrder(1)
                .stepType("ENV_SETUP")
                .stepConfig("{\"javaHome\":\"" + extractJavaHome() + "\",\"classpathEntries\":[],\"extraEnvVars\":{}}")
                .enabled("Y")
                .continueOnFailure("N")
                .build());

        // Step 2: SHELL_EXEC that fails but continue on failure
        stepRepo.save(JobStep.builder()
                .jobDefinition(job)
                .stepName("will-fail")
                .stepOrder(2)
                .stepType("SHELL_EXEC")
                .stepConfig("{\"command\":\"nonexistent_command_that_does_not_exist\"}")
                .enabled("Y")
                .continueOnFailure("Y")
                .build());

        // Step 3: ENV_SETUP again (should still run because step 2 had continueOnFailure)
        stepRepo.save(JobStep.builder()
                .jobDefinition(job)
                .stepName("second-setup")
                .stepOrder(3)
                .stepType("ENV_SETUP")
                .stepConfig("{\"javaHome\":\"" + extractJavaHome() + "\",\"classpathEntries\":[],\"extraEnvVars\":{}}")
                .enabled("Y")
                .continueOnFailure("N")
                .build());

        stepRepo.flush();
        entityManager.clear();
        job = jobRepo.findByIdWithSteps(job.getJobId()).orElseThrow();

        JobRun run = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .status(RunStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        run = runRepo.save(run);

        ExecutionContext ctx = buildContext(run.getRunId(), job.getJobId());
        orchestrator.execute(ctx, job, run);

        runRepo.flush();
        entityManager.clear();
        run = runRepo.findById(run.getRunId()).orElseThrow();
        // Orchestrator sets PARTIAL when any step failed, even with continueOnFailure=Y —
        // the flag only controls whether remaining steps still execute.
        assertEquals(RunStatus.PARTIAL, run.getStatus());
    }

    @Test
    void execute_unregistered_step_type_fails_gracefully() throws Exception {
        JobDefinition job = JobDefinition.builder()
                .jobName("unregistered-type-job")
                .workingDir(System.getProperty("user.dir"))
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        job = jobRepo.save(job);

        // Step references a step type with no registered executor
        stepRepo.save(JobStep.builder()
                .jobDefinition(job)
                .stepName("nonexistent-step")
                .stepOrder(1)
                .stepType("NONEXISTENT_TYPE")
                .stepConfig("{}")
                .enabled("Y")
                .continueOnFailure("N")
                .build());

        stepRepo.flush();
        entityManager.clear();
        job = jobRepo.findByIdWithSteps(job.getJobId()).orElseThrow();

        JobRun run = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .status(RunStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        run = runRepo.save(run);

        ExecutionContext ctx = buildContext(run.getRunId(), job.getJobId());
        orchestrator.execute(ctx, job, run);

        runRepo.flush();
        entityManager.clear();
        run = runRepo.findById(run.getRunId()).orElseThrow();
        assertEquals(RunStatus.PARTIAL, run.getStatus());
    }

    @Test
    void execute_missing_required_config_field_fails_validation() throws Exception {
        JobDefinition job = JobDefinition.builder()
                .jobName("missing-config-job")
                .workingDir(System.getProperty("user.dir"))
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        job = jobRepo.save(job);

        // HTTP_CALL has "url" as a required field — omit it
        stepRepo.save(JobStep.builder()
                .jobDefinition(job)
                .stepName("http-no-url")
                .stepOrder(1)
                .stepType("HTTP_CALL")
                .stepConfig("{\"method\":\"GET\"}") // missing required "url" field
                .enabled("Y")
                .continueOnFailure("N")
                .build());

        stepRepo.flush();
        entityManager.clear();
        job = jobRepo.findByIdWithSteps(job.getJobId()).orElseThrow();

        JobRun run = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .status(RunStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        run = runRepo.save(run);

        ExecutionContext ctx = buildContext(run.getRunId(), job.getJobId());
        orchestrator.execute(ctx, job, run);

        runRepo.flush();
        entityManager.clear();
        run = runRepo.findById(run.getRunId()).orElseThrow();
        assertEquals(RunStatus.PARTIAL, run.getStatus());
    }

    private static String extractJavaHome() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && javaHome.endsWith("jre")) {
            javaHome = javaHome.substring(0, javaHome.length() - 3);
        }
        return javaHome != null ? javaHome.replace("\\", "/") : javaHome;
    }
}
