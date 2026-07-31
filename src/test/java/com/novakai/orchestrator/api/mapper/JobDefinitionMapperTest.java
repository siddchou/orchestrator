package com.novakai.orchestrator.api.mapper;

import com.novakai.orchestrator.api.dto.JobDefinitionRequest;
import com.novakai.orchestrator.api.dto.JobRunDetail;
import com.novakai.orchestrator.api.dto.JobRunSummary;
import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.entity.JobRunStep;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.domain.enums.TriggerType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobDefinitionMapperTest {

    private final JobDefinitionMapper mapper = new JobDefinitionMapper();

    @Test
    void toResponse_maps_fields() {
        JobDefinition job = JobDefinition.builder()
                .jobId(1L)
                .jobName("test")
                .description("desc")
                .workingDir("/tmp")
                .javaHome("/usr/lib/jvm")
                .enabled("Y")
                .steps(List.of())
                .envVars(List.of())
                .build();

        var response = mapper.toResponse(job);
        assertEquals(1L, response.jobId());
        assertEquals("test", response.jobName());
        assertTrue(response.enabled());
    }

    @Test
    void toEntity_maps_request_to_entity() {
        JobDefinitionRequest request = new JobDefinitionRequest(
                "new-name", "new-desc", "/new-dir", "/new-java",
                List.of("lib/a.jar", "lib/b.jar"));
        JobDefinition target = new JobDefinition();
        mapper.toEntity(request, target);

        assertEquals("new-name", target.getJobName());
        assertEquals("/new-dir", target.getWorkingDir());
        assertNotNull(target.getClasspath());
        assertTrue(target.getClasspath().contains("a.jar"));
    }

    @Test
    void toResponse_empty_classpath() {
        JobDefinition job = JobDefinition.builder()
                .jobId(1L)
                .jobName("test")
                .workingDir("/tmp")
                .classpath(null)
                .enabled("Y")
                .steps(List.of())
                .envVars(List.of())
                .build();

        var response = mapper.toResponse(job);
        assertNotNull(response.classpathEntries());
        assertTrue(response.classpathEntries().isEmpty());
    }

    @Test
    void toRunSummary_maps_run() {
        JobDefinition job = JobDefinition.builder()
                .jobId(1L)
                .jobName("test-job")
                .workingDir("/tmp")
                .enabled("Y")
                .build();

        JobRun run = JobRun.builder()
                .runId(10L)
                .jobDefinition(job)
                .status(RunStatus.SUCCESS)
                .triggerType(TriggerType.MANUAL)
                .triggeredBy("admin")
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .endedAt(LocalDateTime.now())
                .build();

        JobRunSummary summary = mapper.toRunSummary(run);
        assertEquals(10L, summary.runId());
        assertEquals(RunStatus.SUCCESS, summary.status());
        assertEquals("test-job", summary.jobName());
        assertTrue(summary.durationSeconds() >= 0);
    }

    @Test
    void toRunDetail_maps_with_steps() {
        JobDefinition job = JobDefinition.builder()
                .jobId(1L)
                .jobName("test-job")
                .workingDir("/tmp")
                .enabled("Y")
                .build();

        JobStep step = JobStep.builder()
                .stepId(1L)
                .stepName("env-setup")
                .stepType("ENV_SETUP")
                .stepOrder(1)
                .build();

        JobRun run = JobRun.builder()
                .runId(10L)
                .jobDefinition(job)
                .status(RunStatus.SUCCESS)
                .triggerType(TriggerType.MANUAL)
                .triggeredBy("admin")
                .startedAt(LocalDateTime.now().minusMinutes(10))
                .endedAt(LocalDateTime.now())
                .build();

        JobRunStep runStep = JobRunStep.builder()
                .runStepId(1L)
                .jobRun(run)
                .jobStep(step)
                .status(RunStatus.SUCCESS)
                .exitCode(0)
                .stepOrder(1)
                .startedAt(LocalDateTime.now().minusMinutes(9))
                .endedAt(LocalDateTime.now().minusMinutes(5))
                .build();

        JobRunDetail detail = mapper.toRunDetail(run, List.of(runStep));
        assertEquals(10L, detail.runId());
        assertEquals(1, detail.steps().size());
        assertEquals("env-setup", detail.steps().get(0).stepName());
    }

    @Test
    void toRunSummary_duration_zero_when_not_started() {
        JobDefinition job = JobDefinition.builder()
                .jobId(1L)
                .jobName("test")
                .workingDir("/tmp")
                .enabled("Y")
                .build();

        JobRun run = JobRun.builder()
                .runId(10L)
                .jobDefinition(job)
                .status(RunStatus.PENDING)
                .triggerType(TriggerType.MANUAL)
                .triggeredBy("admin")
                .build();

        JobRunSummary summary = mapper.toRunSummary(run);
        assertEquals(0, summary.durationSeconds());
    }
}
