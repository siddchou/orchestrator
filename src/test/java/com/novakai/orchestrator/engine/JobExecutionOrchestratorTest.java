package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobRunRepository;
import com.novakai.orchestrator.repository.JobStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobExecutionOrchestratorTest {

    @Autowired
    private JobExecutionOrchestrator orchestrator;

    @Autowired
    private JobDefinitionRepository jobRepo;

    @Autowired
    private JobRunRepository runRepo;

    @Autowired
    private JobStepRepository stepRepo;

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
    void execute_single_step_success() throws Exception {
        // Skip on Windows - ENV_SETUP executor requires real filesystem paths
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return;
        }
        JobDefinition job = JobDefinition.builder()
                .jobName("orch-test")
                .workingDir(System.getProperty("user.dir"))
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        job = jobRepo.save(job);

        JobStep step = JobStep.builder()
                .jobDefinition(job)
                .stepName("env-setup")
                .stepOrder(1)
                .stepType(StepType.ENV_SETUP)
                .stepConfig("{\"javaHome\":\"" + extractJavaHome() + "\",\"classpathEntries\":[],\"extraEnvVars\":null}")
                .enabled("Y")
                .continueOnFailure("N")
                .build();
        stepRepo.save(step);

        JobRun run = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .status(RunStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        run = runRepo.save(run);

        ExecutionContext ctx = buildContext(run.getRunId(), job.getJobId());

        orchestrator.executeSingleStep(ctx, job, run, step);

        runRepo.flush();
        run = runRepo.findById(run.getRunId()).orElseThrow();
        assertEquals(RunStatus.SUCCESS, run.getStatus());
    }

    @Test
    void execute_full_job() throws Exception {
        JobDefinition job = JobDefinition.builder()
                .jobName("full-job")
                .workingDir(System.getProperty("user.dir"))
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        job = jobRepo.save(job);

        JobStep step = JobStep.builder()
                .jobDefinition(job)
                .stepName("env-setup")
                .stepOrder(1)
                .stepType(StepType.ENV_SETUP)
                .stepConfig("{\"javaHome\":\"" + extractJavaHome() + "\",\"classpathEntries\":[],\"extraEnvVars\":null}")
                .enabled("Y")
                .continueOnFailure("N")
                .build();
        stepRepo.save(step);

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
        run = runRepo.findById(run.getRunId()).orElseThrow();
        assertEquals(RunStatus.SUCCESS, run.getStatus());
        assertNotNull(run.getStartedAt());
        assertNotNull(run.getEndedAt());
    }

    @Test
    void execute_cancelled_run() throws Exception {
        JobDefinition job = JobDefinition.builder()
                .jobName("cancel-job")
                .workingDir(System.getProperty("user.dir"))
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        job = jobRepo.save(job);

        JobStep step = JobStep.builder()
                .jobDefinition(job)
                .stepName("env-setup")
                .stepOrder(1)
                .stepType(StepType.ENV_SETUP)
                .stepConfig("{\"javaHome\":\"" + extractJavaHome() + "\",\"classpathEntries\":[],\"extraEnvVars\":null}")
                .enabled("Y")
                .continueOnFailure("N")
                .build();
        stepRepo.save(step);

        job = jobRepo.findByIdWithSteps(job.getJobId()).orElseThrow();

        JobRun run = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .status(RunStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        run = runRepo.save(run);

        ExecutionContext ctx = buildContext(run.getRunId(), job.getJobId());
        ctx.setCancelRequested(true);

        orchestrator.execute(ctx, job, run);

        runRepo.flush();
        run = runRepo.findById(run.getRunId()).orElseThrow();
        assertEquals(RunStatus.CANCELLED, run.getStatus());
    }

    private static String extractJavaHome() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && javaHome.endsWith("jre")) {
            return javaHome.substring(0, javaHome.length() - 3);
        }
        return javaHome;
    }
}
