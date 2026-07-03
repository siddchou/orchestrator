package com.novakai.orchestrator.engine;

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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

@Service
@Slf4j
public class JobLaunchService {

    private final JobDefinitionRepository jobRepo;
    private final JobEnvVarRepository envVarRepo;
    private final JobRunRepository runRepo;
    private final JobStepRepository stepRepo;
    private final JobExecutionOrchestrator orchestrator;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final JsonParser jsonParser;

    private final ConcurrentHashMap<Long, Future<?>> activeFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ExecutionContext> activeContexts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<String>> liveLogQueues
            = new ConcurrentHashMap<>();

    public JobLaunchService(JobDefinitionRepository jobRepo,
                            JobEnvVarRepository envVarRepo,
                            JobRunRepository runRepo,
                            JobStepRepository stepRepo,
                            JobExecutionOrchestrator orchestrator,
                            ThreadPoolTaskExecutor taskExecutor,
                            JsonParser jsonParser) {
        this.jobRepo = jobRepo;
        this.envVarRepo = envVarRepo;
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
        this.orchestrator = orchestrator;
        this.taskExecutor = taskExecutor;
        this.jsonParser = jsonParser;
    }

    public JobRun launch(Long jobId, TriggerType triggerType, String triggeredBy) {
        log.debug("Launching job id={} trigger={} by {}", jobId, triggerType, triggeredBy);
        JobDefinition job = jobRepo.findByIdWithSteps(jobId)
            .orElseThrow(() -> new JobNotFoundException(jobId));

        if (runRepo.existsByJobDefinition_JobIdAndStatus(jobId, RunStatus.RUNNING)) {
            throw new JobAlreadyRunningException(jobId);
        }

        final JobRun run = runRepo.save(JobRun.builder()
            .jobDefinition(job)
            .triggerType(triggerType)
            .triggeredBy(triggeredBy)
            .status(RunStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build());
        final Long runId = run.getRunId();

        ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
        liveLogQueues.put(runId, logQueue);

        ExecutionContext ctx = buildContext(runId, jobId, job, logQueue);

        Future<?> future = taskExecutor.submit(
            () -> {
                MDC.put("runId", String.valueOf(runId));
                try {
                    orchestrator.execute(ctx, job, run);
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

    public JobRun launchByName(String jobName, TriggerType triggerType, String triggeredBy) {
        JobDefinition job = jobRepo.findByJobNameWithSteps(jobName)
            .orElseThrow(() -> new JobNotFoundException(jobName));

        Long jobId = job.getJobId();

        if (runRepo.existsByJobDefinition_JobIdAndStatus(jobId, RunStatus.RUNNING)) {
            throw new JobAlreadyRunningException(jobId);
        }

        final JobRun run = runRepo.save(JobRun.builder()
            .jobDefinition(job)
            .triggerType(triggerType)
            .triggeredBy(triggeredBy)
            .status(RunStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build());
        final Long runId = run.getRunId();

        ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
        liveLogQueues.put(runId, logQueue);

        ExecutionContext ctx = buildContext(runId, jobId, job, logQueue);

        Future<?> future = taskExecutor.submit(
            () -> {
                MDC.put("runId", String.valueOf(runId));
                try {
                    orchestrator.execute(ctx, job, run);
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

    public JobRun launchStep(Long stepId, TriggerType triggerType, String triggeredBy) {
        JobStep step = stepRepo.findStepWithJobDefinition(stepId)
            .orElseThrow(() -> new StepNotFoundException(stepId));

        JobDefinition job = step.getJobDefinition();
        Long jobId = job.getJobId();

        if (runRepo.existsByJobDefinition_JobIdAndStatus(jobId, RunStatus.RUNNING)) {
            throw new JobAlreadyRunningException(jobId);
        }

        final JobRun run = runRepo.save(JobRun.builder()
            .jobDefinition(job)
            .triggerType(triggerType)
            .triggeredBy(triggeredBy)
            .status(RunStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build());
        final Long runId = run.getRunId();

        ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
        liveLogQueues.put(runId, logQueue);

        ExecutionContext ctx = buildContext(runId, jobId, job, logQueue);

        Future<?> future = taskExecutor.submit(
            () -> {
                MDC.put("runId", String.valueOf(runId));
                try {
                    orchestrator.executeSingleStep(ctx, job, run, step);
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

    public JobRun launchStepByName(String stepName, TriggerType triggerType, String triggeredBy) {
        JobStep step = stepRepo.findStepWithJobDefinitionByStepName(stepName)
            .orElseThrow(() -> new StepNotFoundException(stepName));

        JobDefinition job = step.getJobDefinition();
        Long jobId = job.getJobId();

        if (runRepo.existsByJobDefinition_JobIdAndStatus(jobId, RunStatus.RUNNING)) {
            throw new JobAlreadyRunningException(jobId);
        }

        final JobRun run = runRepo.save(JobRun.builder()
            .jobDefinition(job)
            .triggerType(triggerType)
            .triggeredBy(triggeredBy)
            .status(RunStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build());
        final Long runId = run.getRunId();

        ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
        liveLogQueues.put(runId, logQueue);

        ExecutionContext ctx = buildContext(runId, jobId, job, logQueue);

        Future<?> future = taskExecutor.submit(
            () -> {
                MDC.put("runId", String.valueOf(runId));
                try {
                    orchestrator.executeSingleStep(ctx, job, run, step);
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

    public ConcurrentLinkedQueue<String> getLiveLogQueue(Long runId) {
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
                                          ConcurrentLinkedQueue<String> logQueue) {
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
}
