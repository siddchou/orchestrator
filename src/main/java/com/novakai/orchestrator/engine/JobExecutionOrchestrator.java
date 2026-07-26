package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.entity.JobRunStep;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.engine.service.CredentialDecryptionService;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.RetryPolicy;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepExecutor;
import com.novakai.orchestrator.engine.spi.StepResult;
import com.novakai.orchestrator.engine.spi.StepExecutorRegistry;
import com.novakai.orchestrator.repository.JobCredentialRepository;
import com.novakai.orchestrator.repository.JobRunRepository;
import com.novakai.orchestrator.repository.JobRunStepRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

@Component
@Slf4j
public class JobExecutionOrchestrator {

    private final JobRunRepository runRepo;
    private final JobRunStepRepository runStepRepo;
    private final StepExecutorRegistry registry;
    private final JobCredentialRepository credentialRepo;
    private final CredentialDecryptionService decryptionService;
    private final JsonParser jsonParser;

    public JobExecutionOrchestrator(JobRunRepository runRepo,
                                    JobRunStepRepository runStepRepo,
                                    StepExecutorRegistry registry,
                                    JobCredentialRepository credentialRepo,
                                    CredentialDecryptionService decryptionService,
                                    JsonParser jsonParser) {
        this.runRepo = runRepo;
        this.runStepRepo = runStepRepo;
        this.registry = registry;
        this.credentialRepo = credentialRepo;
        this.decryptionService = decryptionService;
        this.jsonParser = jsonParser;
    }

    public void execute(ExecutionContext oldCtx, JobDefinition job, JobRun run) {
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        runRepo.save(run);
        log.debug("Run {} started for job {} with {} steps", oldCtx.getRunId(), job.getJobName(), job.getSteps().size());

        boolean anyStepFailed = false;

        try {
            var steps = job.getSteps().stream()
                .filter(s -> "Y".equals(s.getEnabled()))
                .toList();

            for (JobStep step : steps) {
                if (oldCtx.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                    log.info("Run {} cancelled before step {}", oldCtx.getRunId(), step.getStepName());
                    break;
                }

                JobRunStep runStep = createRunStep(run, step);
                boolean stepFailed = executeStep(oldCtx, run, runStep, step);
                if (stepFailed) {
                    anyStepFailed = true;
                    if ("N".equals(step.getContinueOnFailure())) {
                        log.error("Step {} failed and continueOnFailure=N. Aborting run.", step.getStepName());
                        break;
                    }
                }
            }

        } finally {
            boolean cancelled = oldCtx.isCancelRequested() || Thread.currentThread().isInterrupted();
            run.setEndedAt(LocalDateTime.now());
            if (cancelled) {
                run.setStatus(RunStatus.CANCELLED);
                markRemainingStepsCancelled(oldCtx.getRunId());
            } else {
                run.setStatus(anyStepFailed ? RunStatus.PARTIAL : RunStatus.SUCCESS);
            }
            log.debug("Run {} completed with status {}", run.getRunId(), run.getStatus());
            runRepo.save(run);
        }
    }

    public void executeSingleStep(ExecutionContext oldCtx, JobDefinition job, JobRun run, JobStep targetStep) {
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        runRepo.save(run);

        boolean stepFailed = false;

        try {
            JobRunStep runStep = createRunStep(run, targetStep);
            stepFailed = executeStep(oldCtx, run, runStep, targetStep);

        } finally {
            boolean cancelled = oldCtx.isCancelRequested() || Thread.currentThread().isInterrupted();
            run.setEndedAt(LocalDateTime.now());
            if (cancelled) {
                run.setStatus(RunStatus.CANCELLED);
                markRemainingStepsCancelled(oldCtx.getRunId());
            } else {
                run.setStatus(stepFailed ? RunStatus.FAILED : RunStatus.SUCCESS);
            }
            runRepo.save(run);
        }
    }

    boolean executeStep(ExecutionContext oldCtx, JobRun run, JobRunStep runStep, JobStep step) {
        String stepType = step.getStepType();
        StepExecutor executor = registry.get(stepType)
            .orElse(null);

        if (executor == null) {
            log.error("No executor registered for step type: {}", stepType);
            runStep.setStatus(RunStatus.FAILED);
            runStep.setLogOutput("EXCEPTION: No executor registered for step type: " + stepType);
            runStep.setEndedAt(LocalDateTime.now());
            runStepRepo.save(runStep);
            return true;
        }

        StepContext ctx = buildStepContext(oldCtx, run, step);

        // Pre-execute required-field validation
        String validationError = validateRequiredFields(executor, step.getStepConfig());
        if (validationError != null) {
            log.error("Step {} failed config validation: {}", step.getStepName(), validationError);
            StepResult result = StepResult.failure(validationError, Duration.ZERO);
            persistRunStep(runStep, result);
            return true;
        }

        // Apply retry policy
        RetryPolicy policy = executor.defaultRetryPolicy();
        long startTime = System.nanoTime();
        StepResult result = StepResult.failure("Executor failed to initialize", Duration.ZERO);

        try {
            int maxAttempts = Math.max(1, 1 + policy.retries());
            Duration delay = policy.delayBetweenAttempts();
            int attempt = 0;

            while (attempt < maxAttempts) {
                attempt++;
                try {
                    result = executor.execute(ctx);
                    if (result.isSuccess()) {
                        break; // success — exit retry loop
                    }
                } catch (Exception ex) {
                    log.warn("Step {} attempt {} threw: {}", step.getStepName(), attempt, ex.getMessage());
                    result = StepResult.failure(ex.getMessage(), Duration.ZERO);
                }

                if (attempt < maxAttempts && delay != null) {
                    try {
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Unexpected error in step {}: {}", step.getStepName(), ex.getMessage(), ex);
            result = StepResult.failure("EXCEPTION: " + ex.getMessage(), Duration.ZERO);
        }

        result = new StepResult(
            result.status(),
            result.outputs(),
            result.message(),
            Duration.ofNanos(System.nanoTime() - startTime)
        );

        persistRunStep(runStep, result);
        return !result.isSuccess();
    }

    private void persistRunStep(JobRunStep runStep, StepResult result) {
        runStep.setExitCode(result.getExitCode());
        runStep.setLogOutput(result.getLogOutput());
        runStep.setEndedAt(LocalDateTime.now());

        if (result.isSuccess()) {
            runStep.setStatus(RunStatus.SUCCESS);
            log.debug("Step succeeded (exit code={})", result.getExitCode());
        } else {
            runStep.setStatus(RunStatus.FAILED);
            log.debug("Step failed (exit code={})", result.getExitCode());
        }
        runStepRepo.save(runStep);
    }

    private StepContext buildStepContext(ExecutionContext oldCtx, JobRun run, JobStep step) {
        BlockingQueue<String> logQueue = oldCtx.getLiveLogQueue();
        StepContext.LogSink logSink = new StepContext.LogSink(logQueue);

        StepContext.CredentialResolver credentialResolver = ref -> {
            var cred = credentialRepo.findByCredentialRef(ref)
                .orElseThrow(() -> new RuntimeException("Credential not found: " + ref));
            try {
                return decryptionService.decrypt(cred.getCredValue());
            } catch (Exception e) {
                throw new RuntimeException("Failed to decrypt credential: " + ref, e);
            }
        };

        return StepContext.builder()
            .runId(oldCtx.getRunId())
            .jobId(oldCtx.getJobId())
            .stepId(String.valueOf(step.getStepId()))
            .stepConfig(step.getStepConfig())
            .javaHome(oldCtx.getJavaHome())
            .classpath(new ArrayList<>(oldCtx.getClasspath() != null ? oldCtx.getClasspath() : List.of()))
            .envVars(new HashMap<>(oldCtx.getEnvVars()))
            .logSink(logSink)
            .credentials(credentialResolver)
            .workDir(oldCtx.getWorkingDir() != null ? Path.of(oldCtx.getWorkingDir()) : null)
            .upstreamOutputs(Map.of()) // empty in Phase 1; populated starting Phase 3
            .build();
    }

    /**
     * Validate that all required config fields are present and non-blank.
     * Returns null if valid, or an error message listing missing fields.
     */
    private String validateRequiredFields(StepExecutor executor, String stepConfig) {
        if (stepConfig == null || stepConfig.isBlank()) {
            return "Missing required config field(s): [all] — config JSON is empty";
        }

        try {
            Map<String, Object> configMap = jsonParser.parse(stepConfig, Map.class);
            if (configMap == null) return null;

            StepConfigSchema schema = executor.getConfigSchema();
            List<String> missing = new ArrayList<>();

            for (FieldDefinition field : schema.fields()) {
                if (!field.required()) continue;
                Object value = configMap.get(field.name());
                if (value == null) {
                    missing.add(field.name());
                } else if (value instanceof String s && s.isBlank()) {
                    missing.add(field.name());
                }
            }

            if (missing.isEmpty()) return null;
            return "Missing required config field(s): [" + String.join(", ", missing) + "]";
        } catch (Exception e) {
            // If we can't parse the JSON, let the executor handle it with its own error message.
            log.debug("Could not parse step config for validation: {}", e.getMessage());
            return null;
        }
    }

    private JobRunStep createRunStep(JobRun run, JobStep step) {
        return JobRunStep.builder()
            .jobRun(run)
            .jobStep(step)
            .stepOrder(step.getStepOrder())
            .status(RunStatus.PENDING)
            .build();
    }

    @Transactional
    void markRemainingStepsCancelled(Long runId) {
        List<JobRunStep> incomplete = runStepRepo.findIncompleteStepsByRunId(runId);
        incomplete.forEach(s -> {
            s.setStatus(RunStatus.CANCELLED);
            if (s.getEndedAt() == null) {
                s.setEndedAt(LocalDateTime.now());
            }
        });
        runStepRepo.saveAll(incomplete);
    }
}
