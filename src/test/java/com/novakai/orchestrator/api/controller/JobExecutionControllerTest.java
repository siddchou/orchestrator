package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobRunRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JobExecutionControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @Autowired
    private JobDefinitionRepository jobRepo;

    @Autowired
    private JobRunRepository runRepo;

    private Long savedJobId;

    String base() { return "http://localhost:" + port; }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
            
        runRepo.deleteAll();
        jobRepo.deleteAll();
        JobDefinition job = JobDefinition.builder()
                .jobName("test-job")
                .workingDir("/tmp/test")
                .enabled("Y")
                .build();
        job = jobRepo.save(job);
        savedJobId = job.getJobId();

        JobRun run = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .triggeredBy("test")
                .status(RunStatus.SUCCESS)
                .createdAt(LocalDateTime.now().minusDays(1))
                .startedAt(LocalDateTime.now().minusDays(1))
                .endedAt(LocalDateTime.now().minusDays(1))
                .build();
        runRepo.save(run);
    }

    @Test
    void list_runs() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/runs", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"SUCCESS\""));
    }

    @Test
    void list_runs_filtered_by_job_id() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/runs?jobId=" + savedJobId, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void list_runs_filtered_by_status() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/runs?status=SUCCESS", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void get_run_detail() {
        JobRun run = runRepo.findAll().get(0);
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/runs/" + run.getRunId(), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void get_run_not_found() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/runs/99999", String.class);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void cancel_run() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/runs/1/cancel", null, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
