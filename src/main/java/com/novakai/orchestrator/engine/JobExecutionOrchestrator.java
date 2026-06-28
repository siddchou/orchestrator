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
                    } else {
                        runStep.setStatus(RunStatus.FAILED);
                        anyStepFailed = true;
                        if ("N".equals(step.getContinueOnFailure())) {
                            runStepRepo.save(runStep);
                            log.error("Step {} failed and continueOnFailure=N. Aborting run.", step.getStepName());
                            break;
                        }
                    }
                } catch (Exception ex) {
                    log.error("Unexpected error in step {}: {}", step.getStepName(), ex.getMessage(), ex);
                    runStep.setStatus(RunStatus.FAILED);
                    runStep.setLogOutput("EXCEPTION: " + ex.getMessage());
                    runStep.setEndedAt(LocalDateTime.now());
                    anyStepFailed = true;
                    if ("N".equals(step.getContinueOnFailure())) {
                        runStepRepo.save(runStep);
                        break;
                    }
                } finally {
                    runStepRepo.save(runStep);
                }
            }

        } finally {
            run.setEndedAt(LocalDateTime.now());
            if (ctx.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                run.setStatus(RunStatus.CANCELLED);
            } else {
                run.setStatus(anyStepFailed ? RunStatus.PARTIAL : RunStatus.SUCCESS);
            }
            runRepo.save(run);
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
