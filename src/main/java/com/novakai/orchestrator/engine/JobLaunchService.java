package com.novakai.orchestrator.engine;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.engine.exception.JobAlreadyRunningException;
import com.novakai.orchestrator.engine.exception.JobNotFoundException;
import com.novakai.orchestrator.engine.exception.StepNotFoundException;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobEnvVarRepository;
import com.novakai.orchestrator.repository.JobRunRepository;
import com.novakai.orchestrator.repository.JobStepRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import com.novakai.orchestrator.api.dto.JobRunRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Future;

@Service
@Slf4j
public class JobLaunchService {

    @FunctionalInterface
    private interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    private final JobDefinitionRepository jobRepo;
    private final JobEnvVarRepository envVarRepo;
    private final JobRunRepository runRepo;
    private final JobStepRepository stepRepo;
    private final DagExecutionEngine dagEngine;
    private final JobExecutionOrchestrator orchestrator; // for single-step execution only
    private final ThreadPoolTaskExecutor taskExecutor;
    private final JsonParser jsonParser;

    private final ConcurrentHashMap<Long, Future<?>> activeFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ExecutionContext> activeContexts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, BlockingQueue<String>> liveLogQueues
            = new ConcurrentHashMap<>();

    public JobLaunchService(JobDefinitionRepository jobRepo,
                            JobEnvVarRepository envVarRepo,
                            JobRunRepository runRepo,
                            JobStepRepository stepRepo,
                            DagExecutionEngine dagEngine,
                            JobExecutionOrchestrator orchestrator,
                            @Qualifier("jobTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
                            JsonParser jsonParser) {
        this.jobRepo = jobRepo;
        this.envVarRepo = envVarRepo;
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.dagEngine = dagEngine;
        this.orchestrator = orchestrator;
        this.taskExecutor = taskExecutor;
        this.jsonParser = jsonParser;
    }

    public JobRun launch(Long jobId, TriggerType triggerType, String triggeredBy) {
        return launch(jobId, triggerType, triggeredBy, Map.of());
    }

    public JobRun launch(Long jobId, TriggerType triggerType, String triggeredBy,
                         Map<String, Object> runParameters) {
        JobDefinition job = jobRepo.findByIdWithSteps(jobId)
            .orElseThrow(() -> new JobNotFoundException(jobId));
        return launchInternal(job, triggerType, triggeredBy, runParameters,
            (ctx, j, r) -> dagEngine.execute(ctx, j, r));
    }

    public JobRun launchByName(String jobName, TriggerType triggerType, String triggeredBy) {
        return launchByName(jobName, triggerType, triggeredBy, Map.of());
    }

    public JobRun launchByName(String jobName, TriggerType triggerType, String triggeredBy,
                               Map<String, Object> runParameters) {
        JobDefinition job = jobRepo.findByJobNameWithSteps(jobName)
            .orElseThrow(() -> new JobNotFoundException(jobName));
        return launchInternal(job, triggerType, triggeredBy, runParameters,
            (ctx, j, r) -> dagEngine.execute(ctx, j, r));
    }

    public JobRun launchStep(Long stepId, TriggerType triggerType, String triggeredBy) {
        JobStep step = stepRepo.findStepWithJobDefinition(stepId)
            .orElseThrow(() -> new StepNotFoundException(stepId));
        JobDefinition job = step.getJobDefinition();
        return launchInternal(job, triggerType, triggeredBy, Map.of(),
            (ctx, j, r) -> orchestrator.executeSingleStep(ctx, j, r, step));
    }

    public JobRun launchStepByName(String stepName, TriggerType triggerType, String triggeredBy) {
        JobStep step = stepRepo.findStepWithJobDefinitionByStepName(stepName)
            .orElseThrow(() -> new StepNotFoundException(stepName));
        JobDefinition job = step.getJobDefinition();
        return launchInternal(job, triggerType, triggeredBy, Map.of(),
            (ctx, j, r) -> orchestrator.executeSingleStep(ctx, j, r, step));
    }

    private JobRun launchInternal(JobDefinition job, TriggerType triggerType, String triggeredBy,
                                  Map<String, Object> runParameters,
                                  TriConsumer<ExecutionContext, JobDefinition, JobRun> executionAction) {
        Long jobId = job.getJobId();

        if (runRepo.existsByJobDefinition_JobIdAndStatus(jobId, RunStatus.RUNNING)) {
            throw new JobAlreadyRunningException(jobId);
        }

        log.debug("Launching job id={} trigger={} by {}", jobId, triggerType, triggeredBy);

        final JobRun run = runRepo.save(JobRun.builder()
            .jobDefinition(job)
            .triggerType(triggerType)
            .triggeredBy(triggeredBy)
            .status(RunStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build());
        final Long runId = run.getRunId();

        BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
        liveLogQueues.put(runId, logQueue);

        ExecutionContext ctx = buildContext(runId, jobId, job, logQueue,
            runParameters != null ? Collections.unmodifiableMap(runParameters) : Map.of());

        Future<?> future = taskExecutor.submit(
            () -> {
                MDC.put("runId", String.valueOf(runId));
                try {
                    executionAction.accept(ctx, job, run);
                } finally {
                    MDC.clear();
                    cleanupRun(runId);
                }
            }
        );
        activeFutures.put(runId, future);
        activeContexts.put(runId, ctx);

        return run;
    }

    public void cancel(Long runId) {
        log.info("Cancelling run {}", runId);
        Future<?> future = activeFutures.get(runId);
        if (future != null) {
            future.cancel(true);
        }
        ExecutionContext ctx = findContext(runId);
        if (ctx != null) {
            ctx.setCancelRequested(true);
        }
    }

    public BlockingQueue<String> getLiveLogQueue(Long runId) {
        return liveLogQueues.get(runId);
    }

    private Map<String, String> buildEnvMap(Long jobId) {
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        envVarRepo.findByIsGlobal("Y").forEach(v -> env.put(v.getVarName(), v.getVarValue()));
        envVarRepo.findByJobDefinition_JobId(jobId)
            .stream().filter(v -> "N".equals(v.getIsGlobal()))
            .forEach(v -> env.put(v.getVarName(), v.getVarValue()));
        return env;
    }

    private ExecutionContext buildContext(Long runId, Long jobId, JobDefinition job,
                                          BlockingQueue<String> logQueue,
                                          Map<String, Object> runParameters) {
        List<String> classpath = parseJobClasspath(job.getClasspath());
        return ExecutionContext.builder()
            .runId(runId)
            .jobId(jobId)
            .workingDir(job.getWorkingDir())
            .javaHome(job.getJavaHome())
            .classpath(classpath)
            .envVars(buildEnvMap(jobId))
            .liveLogQueue(logQueue)
            .cancelRequested(false)
            .runParameters(runParameters)
            .build();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJobClasspath(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            Object result = jsonParser.parse(json, Object.class);
            if (result instanceof List) {
                return (List<String>) result;
            }
        } catch (Exception ignored) { }
        return new ArrayList<>();
    }

    private ExecutionContext findContext(Long runId) {
        return activeContexts.get(runId);
    }

    private void cleanupRun(Long runId) {
        activeFutures.remove(runId);
        activeContexts.remove(runId);
        liveLogQueues.remove(runId);
    }

    /**
     * Graceful shutdown handler - cancels all active runs.
     * Called when Spring context is closing via @PreDestroy or DisposableBean.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down job executor - cancelling {} active runs", activeFutures.size());

        // Cancel all active futures
        for (Long runId : activeFutures.keySet()) {
            Future<?> future = activeFutures.get(runId);
            if (future != null && !future.isDone() && !future.isCancelled()) {
                future.cancel(true); // interrupt if running
                log.debug("Cancelled run {}", runId);
            }
        }

        // Mark all active contexts as cancelled
        for (Long runId : activeContexts.keySet()) {
            ExecutionContext ctx = activeContexts.get(runId);
            if (ctx != null) {
                ctx.setCancelRequested(true);
            }
        }

        log.info("Job executor shutdown complete");
    }
}
