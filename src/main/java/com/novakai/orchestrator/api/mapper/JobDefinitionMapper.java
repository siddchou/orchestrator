package com.novakai.orchestrator.api.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novakai.orchestrator.api.dto.*;
import com.novakai.orchestrator.domain.entity.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collections;

@Component
public class JobDefinitionMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobDefinitionResponse toResponse(JobDefinition job) {
        List<JobStepResponse> steps = job.getSteps().stream()
                .map(this::toStepResponse)
                .toList();
        List<EnvVarResponse> envVars = job.getEnvVars().stream()
                .map(this::toEnvVarResponse)
                .toList();
        JobScheduleResponse schedule = job.getSchedule() != null
                ? toScheduleResponse(job.getSchedule()) : null;

        return new JobDefinitionResponse(
                job.getJobId(),
                job.getJobName(),
                job.getDescription(),
                job.getWorkingDir(),
                job.getJavaHome(),
                parseClasspath(job.getClasspath()),
                "Y".equals(job.getEnabled()),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                steps,
                envVars,
                schedule
        );
    }

    public JobDefinition toEntity(JobDefinitionRequest request, JobDefinition target) {
        target.setJobName(request.jobName());
        target.setDescription(request.description());
        target.setWorkingDir(request.workingDir());
        target.setJavaHome(request.javaHome());
        target.setClasspath(serializeClasspath(request.classpathEntries()));
        return target;
    }

    public JobStep toStepEntity(JobStepRequest request, JobDefinition job) {
        return JobStep.builder()
                .jobDefinition(job)
                .stepName(request.stepName())
                .stepOrder(request.stepOrder())
                .stepType(request.stepType())
                .stepConfig(request.stepConfig())
                .continueOnFailure(request.continueOnFailure() ? "Y" : "N")
                .enabled(request.enabled() ? "Y" : "N")
                .build();
    }

    public JobStepResponse toStepResponse(JobStep step) {
        return new JobStepResponse(
                step.getStepId(),
                step.getStepName(),
                step.getStepOrder(),
                step.getStepType(),
                step.getStepConfig(),
                "Y".equals(step.getContinueOnFailure()),
                "Y".equals(step.getEnabled())
        );
    }

    public EnvVarResponse toEnvVarResponse(JobEnvVar envVar) {
        return new EnvVarResponse(
                envVar.getEnvId(),
                envVar.getVarName(),
                envVar.getVarValue(),
                "Y".equals(envVar.getIsGlobal())
        );
    }

    public JobScheduleResponse toScheduleResponse(JobSchedule schedule) {
        return new JobScheduleResponse(
                schedule.getScheduleId(),
                schedule.getCronExpression(),
                "Y".equals(schedule.getEnabled()),
                schedule.getNextFireTime()
        );
    }

    @Transactional(readOnly = true)
    public JobRunSummary toRunSummary(JobRun run) {
        long duration = 0;
        if (run.getStartedAt() != null) {
            LocalDateTime end = run.getEndedAt() != null ? run.getEndedAt() : LocalDateTime.now();
            duration = java.time.Duration.between(run.getStartedAt(), end).getSeconds();
        }
        return new JobRunSummary(
                run.getRunId(),
                run.getJobDefinition().getJobId(),
                run.getJobDefinition().getJobName(),
                run.getStatus(),
                run.getTriggerType(),
                run.getTriggeredBy(),
                run.getStartedAt(),
                run.getEndedAt(),
                duration
        );
    }

    @Transactional(readOnly = true)
    public JobRunDetail toRunDetail(JobRun run, List<JobRunStep> runSteps) {
        List<RunStepDetail> steps = runSteps.stream()
                .map(this::toRunStepDetail)
                .toList();
        long duration = 0;
        if (run.getStartedAt() != null && run.getEndedAt() != null) {
            duration = java.time.Duration.between(run.getStartedAt(), run.getEndedAt()).getSeconds();
        }
        return new JobRunDetail(
                run.getRunId(),
                run.getJobDefinition().getJobId(),
                run.getJobDefinition().getJobName(),
                run.getStatus(),
                run.getTriggerType(),
                run.getTriggeredBy(),
                run.getStartedAt(),
                run.getEndedAt(),
                duration,
                steps
        );
    }

    private RunStepDetail toRunStepDetail(JobRunStep runStep) {
        long duration = 0;
        if (runStep.getStartedAt() != null) {
            LocalDateTime end = runStep.getEndedAt() != null ? runStep.getEndedAt() : LocalDateTime.now();
            duration = java.time.Duration.between(runStep.getStartedAt(), end).getSeconds();
        }
        return new RunStepDetail(
                runStep.getRunStepId(),
                runStep.getJobStep().getStepName(),
                runStep.getJobStep().getStepType(),
                runStep.getStepOrder(),
                runStep.getStatus(),
                runStep.getExitCode(),
                runStep.getStartedAt(),
                runStep.getEndedAt(),
                duration
        );
    }

    private List<String> parseClasspath(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String serializeClasspath(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (Exception e) {
            return null;
        }
    }
}