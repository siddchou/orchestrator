package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.engine.exception.JobAlreadyRunningException;
import com.novakai.orchestrator.engine.exception.JobNotFoundException;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobEnvVarRepository;
import com.novakai.orchestrator.repository.JobRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
    private final JobExecutionOrchestrator orchestrator;
    private final ThreadPoolTaskExecutor taskExecutor;

    private final ConcurrentHashMap<Long, Future<?>> activeFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<String>> liveLogQueues
            = new ConcurrentHashMap<>();

    public JobLaunchService(JobDefinitionRepository jobRepo,
                            JobEnvVarRepository envVarRepo,
                            JobRunRepository runRepo,
                            JobExecutionOrchestrator orchestrator,
                            ThreadPoolTaskExecutor taskExecutor) {
        this.jobRepo = jobRepo;
        this.envVarRepo = envVarRepo;
        this.runRepo = runRepo;
        this.orchestrator = orchestrator;
        this.taskExecutor = taskExecutor;
    }

    public JobRun launch(Long jobId, TriggerType triggerType, String triggeredBy) {
        JobDefinition job = jobRepo.findById(jobId)
            .orElseThrow(() -> new JobNotFoundException(jobId));

        if (runRepo.existsByJobDefinition_JobIdAndStatus(jobId, RunStatus.RUNNING)) {
            throw new JobAlreadyRunningException(jobId);
        }

        Map<String, String> env = buildEnvMap(jobId);

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

        ExecutionContext ctx = ExecutionContext.builder()
            .runId(runId)
            .jobId(jobId)
            .workingDir(job.getWorkingDir())
            .envVars(env)
            .liveLogQueue(logQueue)
            .cancelRequested(false)
            .build();

        Future<?> future = taskExecutor.submit(
            () -> orchestrator.execute(ctx, job, run)
        );
        activeFutures.put(runId, future);

        return run;
    }

    public void cancel(Long runId) {
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

    private ExecutionContext findContext(Long runId) {
        ConcurrentLinkedQueue<String> queue = liveLogQueues.get(runId);
        if (queue != null) {
            return ExecutionContext.builder().runId(runId).build();
        }
        return null;
    }
}
