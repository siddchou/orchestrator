package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.entity.JobRunStep;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.repository.JobRunRepository;
import com.novakai.orchestrator.repository.JobRunStepRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class JobExecutionOrchestrator {

    private final JobRunRepository runRepo;
    private final JobRunStepRepository runStepRepo;
    private final StepExecutorFactory executorFactory;

    public JobExecutionOrchestrator(JobRunRepository runRepo,
                                    JobRunStepRepository runStepRepo,
                                    StepExecutorFactory executorFactory) {
        this.runRepo = runRepo;
        this.runStepRepo = runStepRepo;
        this.executorFactory = executorFactory;
    }

    public void execute(ExecutionContext ctx, JobDefinition job, JobRun run) {
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        runRepo.save(run);
        log.debug("Run {} started for job {} with {} steps", ctx.getRunId(), job.getJobName(), job.getSteps().size());

        boolean anyStepFailed = false;

        try {
            var steps = job.getSteps().stream()
                .filter(s -> "Y".equals(s.getEnabled()))
                .toList();

            for (JobStep step : steps) {
                if (ctx.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                    log.info("Run {} cancelled before step {}", ctx.getRunId(), step.getStepName());
                    break;
                }

                JobRunStep runStep = createRunStep(run, step);
                boolean stepFailed = executeStep(ctx, run, runStep, step);
                if (stepFailed) {
                    anyStepFailed = true;
                    if ("N".equals(step.getContinueOnFailure())) {
                        log.error("Step {} failed and continueOnFailure=N. Aborting run.", step.getStepName());
                        break;
                    }
                }
            }

        } finally {
            run.setEndedAt(LocalDateTime.now());
            if (ctx.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                run.setStatus(RunStatus.CANCELLED);
            } else {
                run.setStatus(anyStepFailed ? RunStatus.PARTIAL : RunStatus.SUCCESS);
            }
            log.debug("Run {} completed with status {}", run.getRunId(), run.getStatus());
            runRepo.save(run);
        }
    }

    public void executeSingleStep(ExecutionContext ctx, JobDefinition job, JobRun run, JobStep targetStep) {
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        runRepo.save(run);

        boolean stepFailed = false;

        try {
            JobRunStep runStep = createRunStep(run, targetStep);
            stepFailed = executeStep(ctx, run, runStep, targetStep);

        } finally {
            run.setEndedAt(LocalDateTime.now());
            if (ctx.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                run.setStatus(RunStatus.CANCELLED);
            } else {
                run.setStatus(stepFailed ? RunStatus.FAILED : RunStatus.SUCCESS);
            }
            runRepo.save(run);
        }
    }

    boolean executeStep(ExecutionContext ctx, JobRun run, JobRunStep runStep, JobStep step) {
        try {
            runStep.setStatus(RunStatus.RUNNING);
            runStep.setStartedAt(LocalDateTime.now());
            runStepRepo.save(runStep);

            StepExecutor executor = executorFactory.resolve(step.getStepType());
            StepResult result = executor.execute(ctx, step);

            runStep.setExitCode(result.exitCode());
            runStep.setLogOutput(result.logOutput());
            runStep.setEndedAt(LocalDateTime.now());

            if (result.success()) {
                runStep.setStatus(RunStatus.SUCCESS);
                log.debug("Step {} succeeded (exit code={})", step.getStepName(), result.exitCode());
            } else {
                runStep.setStatus(RunStatus.FAILED);
                log.debug("Step {} failed (exit code={})", step.getStepName(), result.exitCode());
            }
            runStepRepo.save(runStep);

            return !result.success();
        } catch (Exception ex) {
            log.error("Unexpected error in step {}: {}", step.getStepName(), ex.getMessage(), ex);
            runStep.setStatus(RunStatus.FAILED);
            runStep.setLogOutput("EXCEPTION: " + ex.getMessage());
            runStep.setEndedAt(LocalDateTime.now());
            runStepRepo.save(runStep);
            return true;
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
}
