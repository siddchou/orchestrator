package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.api.dto.JobDefinitionRequest;
import com.novakai.orchestrator.api.dto.JobScheduleRequest;
import com.novakai.orchestrator.api.dto.JobStepRequest;
import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobStepDependencyRepository;
import com.novakai.orchestrator.repository.JobStepRepository;
import com.novakai.orchestrator.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JobDefinitionControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @Autowired
    private JobDefinitionRepository jobRepo;

    @Autowired
    private JobStepDependencyRepository stepDepRepo;

    @Autowired
    private JobStepRepository stepRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDetailsService userDetailsService;

    private String adminToken;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());

        // Generate admin token for authenticated requests
        UserDetails adminUser = userDetailsService.loadUserByUsername("admin");
        adminToken = jwtService.generateToken(adminUser);

        stepDepRepo.deleteAll();
        stepRepo.deleteAll();
        jobRepo.deleteAll();
        JobDefinition job = JobDefinition.builder()
                .jobName("test-job")
                .description("Test job")
                .workingDir("/tmp/test")
                .javaHome("/usr/lib/jvm/java-21")
                .enabled("Y")
                .build();
        job = jobRepo.save(job);
        savedJobId = job.getJobId();
    }

    private Long savedJobId;

    String base() { return "http://localhost:" + port; }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + adminToken);
        return headers;
    }

    @Test
    void list_returns_jobs() {
        ResponseEntity<String> response = restTemplate.getForEntity(base() + "/api/jobs", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"SUCCESS\""));
    }

    @Test
    void create_valid_job() {
        JobDefinitionRequest request = new JobDefinitionRequest(
                "new-job", "Description", "/tmp/new", "/usr/lib/jvm/java-21", null);
        HttpEntity<JobDefinitionRequest> entity = new HttpEntity<>(request, authHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                base() + "/api/jobs", HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("\"jobName\":\"new-job\""));
    }

    @Test
    void create_duplicate_name_returns_400() {
        JobDefinitionRequest request = new JobDefinitionRequest(
                "test-job", "Duplicate", "/tmp/dup", null, null);
        HttpEntity<JobDefinitionRequest> entity = new HttpEntity<>(request, authHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                base() + "/api/jobs", HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void create_blank_fields_returns_400() {
        JobDefinitionRequest request = new JobDefinitionRequest(
                "", null, "", null, null);
        HttpEntity<JobDefinitionRequest> entity = new HttpEntity<>(request, authHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                base() + "/api/jobs", HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void get_existing_job() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/jobs/" + savedJobId, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"jobName\":\"test-job\""));
    }

    @Test
    void get_nonexistent_job_returns_404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/jobs/99999", String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void update_job() {
        JobDefinitionRequest request = new JobDefinitionRequest(
                "updated-name", "Updated desc", "/tmp/updated", null, null);
        HttpEntity<JobDefinitionRequest> entity = new HttpEntity<>(request);
        ResponseEntity<String> response = restTemplate.exchange(
                base() + "/api/jobs/" + savedJobId, HttpMethod.PUT, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"jobName\":\"updated-name\""));
    }

    @Test
    void delete_job() {
        restTemplate.delete(base() + "/api/jobs/" + savedJobId);
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/jobs/" + savedJobId, String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void toggle_enabled() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/jobs/" + savedJobId + "/enable", null, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"enabled\":false"));

        response = restTemplate.postForEntity(
                base() + "/api/jobs/" + savedJobId + "/enable", null, String.class);
        assertTrue(response.getBody().contains("\"enabled\":true"));
    }

    @Test
    void add_step() {
        JobStepRequest step = new JobStepRequest(
                "env-setup", 1, "ENV_SETUP", "{}", false, true);
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/jobs/" + savedJobId + "/steps", step, String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("\"stepName\":\"env-setup\""));
    }

    @Test
    void create_schedule() {
        JobScheduleRequest schedule = new JobScheduleRequest("0 0 0 * * *");
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/jobs/" + savedJobId + "/schedule", schedule, String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void get_schedule() {
        JobScheduleRequest schedule = new JobScheduleRequest("0 0 0 * * *");
        restTemplate.postForEntity(
                base() + "/api/jobs/" + savedJobId + "/schedule", schedule, String.class);

        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/jobs/" + savedJobId + "/schedule", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"cronExpression\":\"0 0 0 * * *\""));
    }

    @Test
    void delete_schedule() {
        JobScheduleRequest schedule = new JobScheduleRequest("0 0 0 * * *");
        restTemplate.postForEntity(
                base() + "/api/jobs/" + savedJobId + "/schedule", schedule, String.class);

        restTemplate.delete(base() + "/api/jobs/" + savedJobId + "/schedule");
    }
}
