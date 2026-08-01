package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.entity.JobRunStep;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.entity.JobStepDependency;
import com.novakai.orchestrator.domain.entity.JobStepDependency.EdgeCondition;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.engine.exception.CircularDependencyException;
import com.novakai.orchestrator.engine.service.CredentialDecryptionService;
import com.novakai.orchestrator.notification.service.RunCompletionPublisher;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.RetryPolicy;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepExecutor;
import com.novakai.orchestrator.engine.spi.StepExecutorRegistry;
import com.novakai.orchestrator.engine.spi.StepResult;
import com.novakai.orchestrator.engine.spi.StepStatus;
import com.novakai.orchestrator.engine.template.ParamResolver;
import com.novakai.orchestrator.engine.template.ResolutionContext;
import com.novakai.orchestrator.repository.JobCredentialRepository;
import com.novakai.orchestrator.repository.JobRunRepository;
import com.novakai.orchestrator.repository.JobRunStepRepository;
import com.novakai.orchestrator.repository.JobStepDependencyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class DagExecutionEngine {

    private final JobRunRepository runRepo;
    private final JobRunStepRepository runStepRepo;
    private final StepExecutorRegistry registry;
    private final JobCredentialRepository credentialRepo;
    private final CredentialDecryptionService decryptionService;
    private final JsonParser jsonParser;
    private final ParamResolver paramResolver;
    private final JobStepDependencyRepository dependencyRepo;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final RunCompletionPublisher notificationPublisher;

    public DagExecutionEngine(JobRunRepository runRepo,
                              JobRunStepRepository runStepRepo,
                              StepExecutorRegistry registry,
                              JobCredentialRepository credentialRepo,
                              CredentialDecryptionService decryptionService,
                              JsonParser jsonParser,
                              ParamResolver paramResolver,
                              JobStepDependencyRepository dependencyRepo,
                              @Qualifier("jobTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
                              RunCompletionPublisher notificationPublisher) {
        this.runRepo = runRepo;
        this.runStepRepo = runStepRepo;
        this.registry = registry;
        this.credentialRepo = credentialRepo;
        this.decryptionService = decryptionService;
        this.jsonParser = jsonParser;
        this.paramResolver = paramResolver;
        this.dependencyRepo = dependencyRepo;
        this.taskExecutor = taskExecutor;
        this.notificationPublisher = notificationPublisher;
    }

    public void execute(ExecutionContext ctx, JobDefinition job, JobRun run) {
        List<JobStep> enabledSteps = getEnabledSteps(job);
        if (enabledSteps.isEmpty()) {
            log.info("Run {}: no enabled steps — marking success", ctx.getRunId());
            run.setStatus(RunStatus.SUCCESS);
            run.setStartedAt(LocalDateTime.now());
            run.setEndedAt(LocalDateTime.now());
            runRepo.save(run);
            return;
        }

        List<JobStepDependency> allDeps = loadDependencies(enabledSteps);
        DagGraph graph = buildDag(enabledSteps, allDeps);
        validateAcyclic(graph, enabledSteps.size());

        int maxConcurrency = Math.min(5, enabledSteps.size());
        executeConcurrent(ctx, job, run, graph, maxConcurrency);
    }

    @Transactional
    List<JobStepDependency> loadDependencies(List<JobStep> steps) {
        List<JobStepDependency> deps = new ArrayList<>();
        for (JobStep step : steps) {
            deps.addAll(dependencyRepo.findByStep_StepId(step.getStepId()));
        }
        return deps;
    }

    private List<JobStep> getEnabledSteps(JobDefinition job) {
        return job.getSteps().stream()
                .filter(s -> "Y".equals(s.getEnabled()))
                .toList();
    }

    // ------------------------------------------------------------------
    // DAG construction
    // ------------------------------------------------------------------

    record DagGraph(
            Map<Long, List<JobStep>> upstreams,
            Map<Long, List<EdgeTarget>> downstreams,
            Set<Long> roots
    ) {}

    record EdgeTarget(JobStep dependent, EdgeCondition condition) {}

    private DagGraph buildDag(List<JobStep> steps, List<JobStepDependency> allDependencies) {
        Set<Long> stepIds = new HashSet<>();
        for (JobStep s : steps) stepIds.add(s.getStepId());

        // Index dependencies by downstream step ID
        Map<Long, List<JobStepDependency>> depsByStep = new HashMap<>();
        for (JobStepDependency dep : allDependencies) {
            depsByStep.computeIfAbsent(dep.getStep().getStepId(), k -> new ArrayList<>()).add(dep);
        }

        Map<Long, List<JobStep>> upstreams = new HashMap<>();
        Map<Long, List<EdgeTarget>> downstreams = new HashMap<>();

        for (Long id : stepIds) {
            upstreams.put(id, new ArrayList<>());
            downstreams.put(id, new ArrayList<>());
        }

        for (JobStep step : steps) {
            List<JobStepDependency> deps = depsByStep.getOrDefault(step.getStepId(), List.of());
            for (JobStepDependency dep : deps) {
                JobStep parent = dep.getDependsOnStep();
                if (!stepIds.contains(parent.getStepId())) {
                    log.warn("Dependency {}->{} skipped: upstream step {} not enabled",
                            parent.getStepId(), step.getStepId(), parent.getStepId());
                    continue;
                }
                upstreams.computeIfAbsent(step.getStepId(), k -> new ArrayList<>()).add(parent);
                downstreams.computeIfAbsent(parent.getStepId(), k -> new ArrayList<>())
                        .add(new EdgeTarget(step, dep.getEdgeCondition()));
            }
        }

        Set<Long> roots = new HashSet<>();
        for (Long id : stepIds) {
            if (upstreams.get(id).isEmpty()) roots.add(id);
        }
        if (roots.isEmpty() && !stepIds.isEmpty()) {
            log.warn("DAG has no root nodes (possible cycle involving disabled steps); treating all as roots");
            roots.addAll(stepIds);
        }

        return new DagGraph(upstreams, downstreams, roots);
    }

    // ------------------------------------------------------------------
    // Cycle detection — Kahn's algorithm
    // ------------------------------------------------------------------

    private void validateAcyclic(DagGraph graph, int expectedSize) {
        Map<Long, Integer> inDegree = new HashMap<>();
        for (Long id : graph.upstreams.keySet()) {
            inDegree.put(id, graph.upstreams.get(id).size());
        }

        List<Long> queue = new ArrayList<>();
        for (Long id : inDegree.keySet()) {
            if (inDegree.get(id) == 0) queue.add(id);
        }

        int visited = 0;
        while (!queue.isEmpty()) {
            Long node = queue.removeLast();
            visited++;
            for (EdgeTarget target : graph.downstreams.getOrDefault(node, List.of())) {
                int newDeg = inDegree.get(target.dependent().getStepId()) - 1;
                inDegree.put(target.dependent().getStepId(), newDeg);
                if (newDeg == 0) queue.add(target.dependent().getStepId());
            }
        }

        if (visited < expectedSize) {
            throw new CircularDependencyException(
                    "Cycle detected in step dependencies: " + (expectedSize - visited) + " steps cannot be resolved");
        }
    }

    // ------------------------------------------------------------------
    // Concurrent execution
    // ------------------------------------------------------------------

    private void executeConcurrent(ExecutionContext ctx, JobDefinition job, JobRun run,
                                   DagGraph graph, int maxConcurrency) {
        Map<Long, Integer> remaining = new ConcurrentHashMap<>();
        for (Long id : graph.upstreams.keySet()) {
            remaining.put(id, graph.upstreams.get(id).size());
        }

        Map<Long, StepResult> stepResults = new ConcurrentHashMap<>();
        Map<Long, JobRunStep> runSteps = new ConcurrentHashMap<>();
        Map<Long, LocalDateTime> startTimes = new ConcurrentHashMap<>();
        Set<Long> submittedIds = ConcurrentHashMap.newKeySet();
        Map<String, String> envVars = ctx.getEnvVars() != null
                ? new HashMap<>(ctx.getEnvVars()) : Map.of();

        ResolutionContext resolutionCtx = new ResolutionContext(
                ctx.getRunParameters(),
                new ConcurrentHashMap<String, Map<String, Object>>(),
                envVars
        );

        BlockingQueue<String> logQueue = ctx.getLiveLogQueue();
        AtomicBoolean anyFailed = new AtomicBoolean(false);
        Semaphore semaphore = new Semaphore(maxConcurrency);
        CountDownLatch latch = new CountDownLatch(graph.upstreams.size());

        for (Long rootId : graph.roots) {
            submittedIds.add(rootId);
            submitStep(ctx, job, run, rootId, resolutionCtx, stepResults,
                    runSteps, startTimes, submittedIds, semaphore, latch, anyFailed, graph, logQueue);
        }

        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        finalizeRun(ctx, run, stepResults, runSteps, startTimes, remaining, graph.roots);
    }

    private void submitStep(ExecutionContext ctx, JobDefinition job, JobRun run,
                            Long stepId, ResolutionContext resCtx,
                            Map<Long, StepResult> stepResults,
                            Map<Long, JobRunStep> runSteps,
                            Map<Long, LocalDateTime> startTimes,
                            Set<Long> submittedIds,
                            Semaphore semaphore, CountDownLatch latch,
                            AtomicBoolean anyFailed, DagGraph graph,
                            BlockingQueue<String> logQueue) {
        taskExecutor.execute(() -> {
            boolean acquired = false;
            try {
                if (ctx.isCancelRequested()) {
                    cancelRemaining(stepId, job, run, graph, stepResults, runSteps, startTimes, latch, semaphore);
                    // Count down for this step — cancelRemaining only counts steps it actually marks.
                    boolean alreadyMarked = stepResults.containsKey(stepId);
                    if (!alreadyMarked) {
                        // This shouldn't happen since cancelRemaining marks all, but be safe.
                        latch.countDown();
                    }
                    return;
                }

                semaphore.acquire();
                acquired = true;

                JobStep step = findStep(job, stepId);
                if (step == null) {
                    log.error("Step {} not found in job {}", stepId, job.getJobId());
                    latch.countDown();
                    return;
                }

                // Resolve templates in step config
                Map<String, Object> resolvedConfig = resolveConfig(step.getStepConfig(), resCtx);

                // Collect upstream step results for cross-step template resolution
                Map<String, StepResult> upstreamOutputs = new HashMap<>();
                for (JobStep upstream : graph.upstreams.getOrDefault(stepId, List.of())) {
                    StepResult r = stepResults.get(upstream.getStepId());
                    if (r != null) {
                        upstreamOutputs.put(String.valueOf(upstream.getStepId()), r);
                    }
                }

                LocalDateTime startedAt = LocalDateTime.now();
                StepResult result = executeStepWithRetry(ctx, step, resolvedConfig, logQueue, upstreamOutputs);
                LocalDateTime endedAt = LocalDateTime.now();

                runSteps.put(stepId, createRunStepEntity(run, step, result, startedAt, endedAt));
                startTimes.put(stepId, startedAt);
                stepResults.put(stepId, result);

                if (!result.isSuccess()) {
                    anyFailed.set(true);
                }

                // Update resolution context with this step's outputs for downstream steps
                updateResolutionContext(resCtx, stepId, result);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.error("Unexpected error executing step {}: {}", stepId, ex.getMessage(), ex);
                StepResult errorResult = StepResult.failure("EXCEPTION: " + ex.getMessage(), Duration.ZERO);
                JobStep step = findStep(job, stepId);
                if (step != null) {
                    LocalDateTime now = LocalDateTime.now();
                    runSteps.put(stepId, createRunStepEntity(run, step, errorResult, now, now));
                    startTimes.put(stepId, now);
                }
                stepResults.put(stepId, errorResult);
                anyFailed.set(true);
            } finally {
                if (acquired) semaphore.release();
                // Each step counts down the latch exactly once when it finishes.
                latch.countDown();
            }

            // Signal dependents after this step is done
            signalDependents(ctx, job, run, stepId, resCtx, stepResults, runSteps, startTimes, submittedIds,
                    semaphore, latch, anyFailed, graph, logQueue);
        });
    }

    private void cancelRemaining(Long currentStepId, JobDefinition job, JobRun run, DagGraph graph,
                                 Map<Long, StepResult> stepResults,
                                 Map<Long, JobRunStep> runSteps,
                                 Map<Long, LocalDateTime> startTimes,
                                 CountDownLatch latch, Semaphore semaphore) {
        for (Long id : graph.upstreams.keySet()) {
            if (!stepResults.containsKey(id) && !runSteps.containsKey(id)) {
                StepResult cancelled = StepResult.cancelled("Cancelled", Duration.ZERO);
                JobStep step = findStep(job, id);
                LocalDateTime now = LocalDateTime.now();
                if (step != null) {
                    runSteps.put(id, createRunStepEntity(run, step, cancelled, now, now));
                } else {
                    runSteps.put(id, JobRunStep.builder()
                            .jobRun(run)
                            .status(RunStatus.CANCELLED)
                            .logOutput("Cancelled")
                            .exitCode(-1)
                            .startedAt(now)
                            .endedAt(now)
                            .build());
                }
                startTimes.put(id, now);
                stepResults.put(id, cancelled);
                latch.countDown();
            }
        }
    }

    private void signalDependents(ExecutionContext ctx, JobDefinition job, JobRun run,
                                  Long finishedStepId, ResolutionContext resCtx,
                                  Map<Long, StepResult> stepResults,
                                  Map<Long, JobRunStep> runSteps,
                                  Map<Long, LocalDateTime> startTimes,
                                  Set<Long> submittedIds,
                                  Semaphore semaphore, CountDownLatch latch,
                                  AtomicBoolean anyFailed, DagGraph graph,
                                  BlockingQueue<String> logQueue) {
        List<EdgeTarget> dependents = graph.downstreams.getOrDefault(finishedStepId, List.of());

        for (EdgeTarget edge : dependents) {
            Long dependentId = edge.dependent().getStepId();

            // Atomic claim: only one thread is responsible for submitting this dependent.
            // The claiming thread waits for all upstreams to finish before executing.
            if (!submittedIds.add(dependentId)) continue;

            // Submit a task that waits for remaining upstreams, then executes the step.
            taskExecutor.execute(() -> {
                try {
                    waitForUpstreams(graph.upstreams.get(dependentId), stepResults);

                    if (!canStepProceed(dependentId, graph.upstreams.get(dependentId), stepResults, graph)) {
                        StepResult skipped = StepResult.skipped(
                                "Skipped — no edge condition satisfied", Duration.ZERO);
                        JobStep step = findStep(job, dependentId);
                        LocalDateTime now = LocalDateTime.now();
                        if (step != null) {
                            runSteps.put(dependentId, createRunStepEntity(run, step, skipped, now, now));
                            startTimes.put(dependentId, now);
                        } else {
                            runSteps.put(dependentId, JobRunStep.builder()
                                    .jobRun(run)
                                    .status(RunStatus.SKIPPED)
                                    .logOutput("Skipped — no edge condition satisfied")
                                    .exitCode(-1)
                                    .startedAt(now)
                                    .endedAt(now)
                                    .build());
                        }
                        stepResults.put(dependentId, skipped);
                        latch.countDown();
                        // Signal downstream steps so they can evaluate their own edge conditions.
                        signalDependents(ctx, job, run, dependentId, resCtx, stepResults, runSteps, startTimes,
                                submittedIds, semaphore, latch, anyFailed, graph, logQueue);
                        return;
                    }

                    submitStep(ctx, job, run, dependentId, resCtx, stepResults,
                            runSteps, startTimes, submittedIds, semaphore, latch, anyFailed, graph, logQueue);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    StepResult cancelled = StepResult.cancelled("Cancelled", Duration.ZERO);
                    JobStep step = findStep(job, dependentId);
                    if (step != null) {
                        LocalDateTime now = LocalDateTime.now();
                        runSteps.put(dependentId, createRunStepEntity(run, step, cancelled, now, now));
                        startTimes.put(dependentId, now);
                    }
                    stepResults.put(dependentId, cancelled);
                    latch.countDown();
                    // Signal downstream steps so the chain can continue.
                    signalDependents(ctx, job, run, dependentId, resCtx, stepResults, runSteps, startTimes,
                            submittedIds, semaphore, latch, anyFailed, graph, logQueue);
                }
            });
        }
    }

    /** Waits for all upstream steps to produce a result (success or failure). */
    private void waitForUpstreams(List<JobStep> upstreams, Map<Long, StepResult> results)
            throws InterruptedException {
        if (upstreams == null || upstreams.isEmpty()) return;
        long deadline = System.currentTimeMillis() + 30 * 60_000; // 30 min safety cap
        while (System.currentTimeMillis() < deadline) {
            boolean allDone = upstreams.stream().allMatch(u -> results.containsKey(u.getStepId()));
            if (allDone) return;
            Thread.sleep(200);
        }
        throw new InterruptedException("Timeout waiting for upstream steps to complete");
    }

    /** Checks if any edge into this dependent step is satisfied by the upstream results. */
    private boolean canStepProceed(Long dependentId, List<JobStep> upstreams, Map<Long, StepResult> results, DagGraph graph) {
        for (JobStep upstream : upstreams) {
            StepResult ur = results.get(upstream.getStepId());
            if (ur == null) return false;

            // Find the edge condition from this upstream to our dependent.
            List<EdgeTarget> edgesFromUpstream = graph.downstreams.getOrDefault(upstream.getStepId(), List.of());
            for (EdgeTarget et : edgesFromUpstream) {
                if (et.dependent().getStepId().equals(dependentId)) {
                    if (evaluateEdge(ur, et.condition(), dependentId, upstream.getStepId())) {
                        return true; // at least one edge fires — step can proceed
                    }
                }
            }
        }
        return false;
    }

    private boolean evaluateEdge(StepResult upstreamResult, EdgeCondition condition,
                                 Long dependentId, Long upstreamId) {
        return switch (condition) {
            case ON_SUCCESS -> upstreamResult.status() == StepStatus.SUCCESS;
            case ON_FAILURE -> upstreamResult.status() == StepStatus.FAILED;
            case ALWAYS -> true;
        };
    }

    // ------------------------------------------------------------------
    // Finalization
    // ------------------------------------------------------------------

    @Transactional
    private void finalizeRun(ExecutionContext ctx, JobRun run,
                             Map<Long, StepResult> stepResults,
                             Map<Long, JobRunStep> runSteps,
                             Map<Long, LocalDateTime> startTimes,
                             Map<Long, Integer> remaining,
                             Set<Long> rootIds) {
        boolean cancelled = ctx.isCancelRequested() || Thread.currentThread().isInterrupted();

        if (!runSteps.isEmpty()) {
            runStepRepo.saveAll(new ArrayList<>(runSteps.values()));
        }

        run.setEndedAt(LocalDateTime.now());

        if (cancelled) {
            run.setStatus(RunStatus.CANCELLED);
        } else if (stepResults.values().stream().anyMatch(r -> !r.isSuccess())) {
            boolean anySuccess = stepResults.values().stream().anyMatch(StepResult::isSuccess);
            boolean rootFailed = stepResults.entrySet().stream()
                    .filter(e -> !e.getValue().isSuccess())
                    .anyMatch(e -> rootIds.contains(e.getKey()));
            // If a root failed but no downstream step succeeded (all skipped/blocked) → FAILED.
            // If cleanup/recovery steps ran successfully via ON_FAILURE/ALWAYS → PARTIAL.
            if (!anySuccess && rootFailed) {
                run.setStatus(RunStatus.FAILED);
            } else if (!anySuccess) {
                run.setStatus(RunStatus.FAILED);
            } else {
                run.setStatus(RunStatus.PARTIAL);
            }
        } else {
            run.setStatus(RunStatus.SUCCESS);
        }

        log.info("Run {} completed with status {}", run.getRunId(), run.getStatus());
        runRepo.save(run);

        if (notificationPublisher != null) {
            try {
                notificationPublisher.publish(
                    run.getRunId(),
                    run.getJobDefinition().getJobId(),
                    run.getJobDefinition().getJobName(),
                    run.getStatus(),
                    run.getEndedAt(),
                    run.getTriggeredBy()
                );
            } catch (Exception e) {
                log.error("Failed to publish notification event for run {}: {}", run.getRunId(), e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Step helpers
    // ------------------------------------------------------------------

    private JobStep findStep(JobDefinition job, Long stepId) {
        return job.getSteps().stream()
                .filter(s -> s.getStepId().equals(stepId))
                .findFirst().orElse(null);
    }

    private Map<String, Object> resolveConfig(String configJson, ResolutionContext ctx) {
        if (configJson == null || configJson.isBlank()) return Map.of();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = jsonParser.parse(configJson, Map.class);
            if (config != null) paramResolver.resolveInPlace(config, ctx);
            return config != null ? config : Map.of();
        } catch (Exception e) {
            log.warn("Failed to resolve templates in step config: {}", e.getMessage());
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> config = jsonParser.parse(configJson, Map.class);
                return config != null ? config : Map.of();
            } catch (Exception ignore) {
                return Map.of();
            }
        }
    }

    private void updateResolutionContext(ResolutionContext resCtx, Long stepId, StepResult result) {
        if (result.outputs() != null && !result.outputs().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> outputsMap =
                    (Map<String, Map<String, Object>>) resCtx.stepOutputs();
            outputsMap.put(stepId.toString(), new HashMap<>(result.outputs()));
        }
    }

    // ------------------------------------------------------------------
    // Step execution with retry (mirrors JobExecutionOrchestrator logic)
    // ------------------------------------------------------------------

    StepResult executeStepWithRetry(ExecutionContext ctx, JobStep step,
                                    Map<String, Object> resolvedConfig,
                                    BlockingQueue<String> logQueue,
                                    Map<String, StepResult> upstreamOutputs) {
        String stepType = step.getStepType();
        StepExecutor executor = registry.get(stepType).orElse(null);

        if (executor == null) {
            log.error("No executor registered for step type: {}", stepType);
            return StepResult.failure(
                    "EXCEPTION: No executor registered for step type: " + stepType, Duration.ZERO);
        }

        String validationError = validateRequiredFields(executor, resolvedConfig);
        if (validationError != null) {
            log.error("Step {} failed config validation: {}", step.getStepName(), validationError);
            return StepResult.failure(validationError, Duration.ZERO);
        }

        var stepCtx = buildStepContext(ctx, step, resolvedConfig, logQueue, upstreamOutputs);

        RetryPolicy policy = executor.defaultRetryPolicy();
        long startTime = System.nanoTime();
        StepResult result = StepResult.failure("Executor failed to initialize", Duration.ZERO);

        try {
            int maxAttempts = Math.max(1, 1 + policy.retries());
            var delay = policy.delayBetweenAttempts();
            int attempt = 0;

            while (attempt < maxAttempts) {
                attempt++;
                if (ctx.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Run cancelled");
                }
                try {
                    result = executor.execute(stepCtx);
                    if (result.isSuccess()) break;
                } catch (Exception ex) {
                    log.warn("Step {} attempt {} threw: {}", step.getStepName(), attempt, ex.getMessage());
                    result = StepResult.failure(ex.getMessage(), Duration.ZERO);
                }
                if (attempt < maxAttempts && delay != null) {
                    try { Thread.sleep(delay.toMillis()); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            result = StepResult.cancelled("Cancelled", Duration.ZERO);
        } catch (Exception ex) {
            log.error("Unexpected error in step {}: {}", step.getStepName(), ex.getMessage(), ex);
            result = StepResult.failure("EXCEPTION: " + ex.getMessage(), Duration.ZERO);
        }

        return new StepResult(
                result.status(),
                result.outputs(),
                result.message(),
                java.time.Duration.ofNanos(System.nanoTime() - startTime)
        );
    }

    private StepContext buildStepContext(ExecutionContext ctx, JobStep step,
                                         Map<String, Object> resolvedConfig,
                                         BlockingQueue<String> logQueue,
                                         Map<String, StepResult> upstreamOutputs) {
        var logSink = new StepContext.LogSink(logQueue);
        var credentialResolver = (StepContext.CredentialResolver) ref -> {
            var cred = credentialRepo.findByCredentialRef(ref)
                    .orElseThrow(() -> new RuntimeException("Credential not found: " + ref));
            try { return decryptionService.decrypt(cred.getCredValue()); }
            catch (Exception e) { throw new RuntimeException("Failed to decrypt credential: " + ref, e); }
        };

        return StepContext.builder()
                .runId(ctx.getRunId())
                .jobId(ctx.getJobId())
                .stepId(String.valueOf(step.getStepId()))
                .stepConfig(step.getStepConfig())
                .resolvedParams(resolvedConfig)
                .javaHome(ctx.getJavaHome())
                .classpath(new ArrayList<>(ctx.getClasspath() != null ? ctx.getClasspath() : List.of()))
                .envVars(new HashMap<>(ctx.getEnvVars()))
                .logSink(logSink)
                .credentials(credentialResolver)
                .workDir(ctx.getWorkingDir() != null ? Path.of(ctx.getWorkingDir()) : null)
                .upstreamOutputs(upstreamOutputs)
                .build();
    }

    private String validateRequiredFields(StepExecutor executor, Map<String, Object> configMap) {
        if (configMap == null || configMap.isEmpty()) return null;
        try {
            var schema = executor.getConfigSchema();
            List<String> missing = new ArrayList<>();
            for (var field : schema.fields()) {
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
            log.debug("Could not parse step config for validation: {}", e.getMessage());
            return null;
        }
    }

    private JobRunStep createRunStepEntity(JobRun run, JobStep step, StepResult result,
                                           LocalDateTime startedAt, LocalDateTime endedAt) {
        RunStatus status = switch (result.status()) {
            case SUCCESS   -> RunStatus.SUCCESS;
            case FAILED    -> RunStatus.FAILED;
            case SKIPPED   -> RunStatus.SKIPPED;
            case CANCELLED -> RunStatus.CANCELLED;
        };

        return JobRunStep.builder()
                .jobRun(run)
                .jobStep(step)
                .stepOrder(step.getStepOrder())
                .status(status)
                .exitCode(result.getExitCode())
                .logOutput(result.getLogOutput())
                .startedAt(startedAt)
                .endedAt(endedAt)
                .build();
    }
}
