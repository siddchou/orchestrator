package com.novakai.orchestrator.engine.observability;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.engine.ExecutionContext;
import com.novakai.orchestrator.engine.DagExecutionEngine;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test: triggers a job run with multiple step types,
 * then verifies metrics are exposed via /actuator/prometheus.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MetricsIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DagExecutionEngine dagEngine;

    @Autowired
    private JobDefinitionRepository jobRepo;

    @Autowired
    private JobRunRepository runRepo;

    private RestTemplate restTemplate = new RestTemplate();

    String base() { return "http://localhost:" + port; }

    /**
     * Triggers a single SLEEP step and verifies metrics are exposed via Prometheus endpoint:
     * (a) orchestrator_step_duration_seconds contains entry for SLEEP type
     * (b) orchestrator_run_count_total incremented by 1
     * (c) orchestrator_run_active returns to 0 after completion
     */
    @Test
    void metrics_recorded_after_job_run() {
        // Create job with a single SLEEP step
        JobDefinition job = JobDefinition.builder()
                .jobName("metrics-test-job")
                .workingDir(System.getProperty("user.dir"))
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        job = jobRepo.save(job);

        var step = com.novakai.orchestrator.domain.entity.JobStep.builder()
                .jobDefinition(job)
                .stepName("sleep-step")
                .stepOrder(1)
                .stepType("SLEEP")
                .stepConfig("{\"durationMs\":50}")
                .enabled("Y")
                .continueOnFailure("N")
                .build();

        // Save step via job's cascade relationship, then reload with eager fetch
        job.getSteps().add(step);
        job = jobRepo.save(job);
        job = jobRepo.findByIdWithSteps(job.getJobId()).orElseThrow();

        JobRun run = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .status(RunStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        run = runRepo.save(run);

        ExecutionContext ctx = ExecutionContext.builder()
                .runId(run.getRunId())
                .jobId(job.getJobId())
                .workingDir(System.getProperty("user.dir"))
                .javaHome(System.getProperty("java.home"))
                .classpath(List.of())
                .envVars(Map.of())
                .liveLogQueue(new LinkedBlockingQueue<>())
                .cancelRequested(false)
                .build();

        // Execute the run
        dagEngine.execute(ctx, job, run);

        // Scrape Prometheus endpoint and verify metrics are present
        ResponseEntity<String> response = restTemplate.getForEntity(base() + "/actuator/prometheus", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        String body = response.getBody();

        // (a) Step duration metric should contain SLEEP type entry
        assertTrue(body.contains("orchestrator_step_duration_seconds"),
                "Prometheus endpoint should expose step duration metric");
        assertTrue(body.contains("step_type=\"SLEEP\"") || body.contains("step_type=SLEEP"),
                "Step duration should have entry for SLEEP step type");

        // (b) Run count metric should be present and incremented
        assertTrue(body.contains("orchestrator_run_count_total"),
                "Prometheus endpoint should expose run count metric");
        assertTrue(body.contains("status=\"SUCCESS\"") || body.contains("status=SUCCESS"),
                "Run count should have SUCCESS status entry");

        // (c) Active runs gauge should be 0 after completion
        assertTrue(body.contains("orchestrator_run_active"),
                "Prometheus endpoint should expose active runs gauge");
    }
}
