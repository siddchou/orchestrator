package com.novakai.orchestrator.cli;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.entity.JobRunStep;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.engine.JobLaunchService;
import com.novakai.orchestrator.engine.exception.JobAlreadyRunningException;
import com.novakai.orchestrator.engine.exception.JobNotFoundException;
import com.novakai.orchestrator.engine.exception.StepNotFoundException;
import com.novakai.orchestrator.repository.JobRunRepository;
import com.novakai.orchestrator.repository.JobRunStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@Profile("cli")
@Order(Integer.MAX_VALUE)
@Slf4j
@RequiredArgsConstructor
public class CliRunner implements CommandLineRunner {

    private static final Set<RunStatus> TERMINAL_STATUSES = Set.of(
            RunStatus.SUCCESS, RunStatus.FAILED, RunStatus.PARTIAL,
            RunStatus.CANCELLED, RunStatus.SKIPPED
    );

    private final JobLaunchService launchService;
    private final JobRunRepository runRepo;
    private final JobRunStepRepository runStepRepo;

    @Override
    public void run(String... args) {
        if (args.length < 2) {
            log.error("Usage: orchestrator -- <command> <name>");
            log.error("  Commands:");
            log.error("    run-job <job-name>     Run a full job");
            log.error("    run-step <step-name>   Run a single step");
            System.exit(1);
        }

        String command = args[0];
        String name = args[1];

        try {
            JobRun run;

            switch (command) {
                case "run-job" -> run = launchService.launchByName(name, TriggerType.CLI, "cli");
                case "run-step" -> run = launchService.launchStepByName(name, TriggerType.CLI, "cli");
                default -> {
                    log.error("Unknown command: {}", command);
                    log.error("Valid commands: run-job, run-step");
                    System.exit(1);
                    run = null;
                }
            }

            printRunStarted(run);
            waitForCompletion(run.getRunId());
            printResult(run.getRunId());

        } catch (JobNotFoundException ex) {
            log.error("[CLI] Error: Job not found (name={})", name);
            System.exit(1);
        } catch (StepNotFoundException ex) {
            log.error("[CLI] Error: Step not found (name={})", name);
            System.exit(1);
        } catch (JobAlreadyRunningException ex) {
            log.error("[CLI] Error: Job is already running");
            System.exit(1);
        } catch (Exception ex) {
            log.error("[CLI] Error: {}", ex.getMessage(), ex);
            System.exit(1);
        }
    }

    private void printRunStarted(JobRun run) {
        String jobName = run.getJobDefinition() != null ? run.getJobDefinition().getJobName() : "unknown";
        log.info("[CLI] Run #{} started — Job: {}", run.getRunId(), jobName);
    }

    private void waitForCompletion(Long runId) throws InterruptedException {
        while (true) {
            JobRun run = runRepo.findById(runId).orElse(null);
            if (run == null) {
                Thread.sleep(500);
                continue;
            }
            if (TERMINAL_STATUSES.contains(run.getStatus())) {
                break;
            }
            Thread.sleep(500);
        }
    }

    private void printResult(Long runId) {
        JobRun run = runRepo.findById(runId).orElse(null);
        if (run == null) {
            log.error("[CLI] Error: Run not found");
            System.exit(1);
        }

        List<JobRunStep> steps = runStepRepo.findByJobRun_RunIdOrderByStepOrderAsc(runId);
        for (JobRunStep step : steps) {
            String statusStr = String.format("%8s", step.getStatus().name());
            int exit = step.getExitCode() != null ? step.getExitCode() : -1;
            log.info("[CLI] Step {}: {} — {} (exit {})",
                    step.getStepOrder(), step.getJobStep().getStepName(), statusStr, exit);
        }

        log.info("[CLI] Run #{} finished — Status: {}", runId, run.getStatus());

        if (run.getStatus() == RunStatus.SUCCESS) {
            System.exit(0);
        } else {
            System.exit(1);
        }
    }
}
