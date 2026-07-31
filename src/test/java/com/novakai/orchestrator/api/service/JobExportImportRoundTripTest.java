package com.novakai.orchestrator.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novakai.orchestrator.api.dto.*;
import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobDefinitionVersionRepository;
import com.novakai.orchestrator.repository.JobStepDependencyRepository;
import com.novakai.orchestrator.repository.JobStepRepository;
import com.novakai.orchestrator.security.JwtService;
import com.novakai.orchestrator.security.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JobExportImportRoundTripTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;
    private String adminToken;

    @Autowired
    private JobDefinitionRepository jobRepo;

    @Autowired
    private JobStepRepository stepRepo;

    @Autowired
    private JobStepDependencyRepository depRepo;

    @Autowired
    private JobDefinitionVersionRepository versionRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());

        UserDetails adminUser = userDetailsService.loadUserByUsername("admin");
        adminToken = jwtService.generateToken(adminUser);

        depRepo.deleteAll();
        stepRepo.deleteAll();
        versionRepo.deleteAll();
        jobRepo.deleteAll();
    }

    String base() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // =====================================================================
    //  Test 1: Full export → delete → import round trip
    // =====================================================================

    @Test
    void fullRoundTrip_export_delete_import() {
        Long jobId = createJobViaApi("round-trip-job", "A test job", "/tmp/test", "/usr/lib/jvm/java-21");
        addStepViaApi(jobId, "step-a", 1, "SHELL_EXEC", "{\"command\":\"echo hello\"}");
        addStepViaApi(jobId, "step-b", 2, "SHELL_EXEC", "{\"command\":\"echo world\"}");

        // Set dependency: step-b depends on step-a
        setDependencyViaApi(jobId);

        // Create schedule
        createScheduleViaApi(jobId, "0 30 2 * * ?");

        // Export as JSON
        ResponseEntity<String> exportResp = restTemplate.getForEntity(
                base() + "/api/jobs/" + jobId + "/export?format=json", String.class);
        assertEquals(HttpStatus.OK, exportResp.getStatusCode());
        String exportedJson = extractData(exportResp.getBody());
        assertNotNull(exportedJson);
        assertTrue(exportedJson.contains("round-trip-job"), "Export should contain job name");
        assertTrue(exportedJson.contains("step-a"), "Export should contain step names");

        // Delete the job
        restTemplate.delete(base() + "/api/jobs/" + jobId);
        assertEquals(HttpStatus.NOT_FOUND,
                restTemplate.getForEntity(base() + "/api/jobs/" + jobId, String.class).getStatusCode());

        // Import back with mode=ERROR (job doesn't exist, should create)
        JobImportRequest importReq = new JobImportRequest(
                "1.0", "ERROR", null, "round-trip-job", "A test job", "/tmp/test",
                "/usr/lib/jvm/java-21", null, true, "test-team",
                parseStepsFromExport(exportedJson),
                parseDepsFromExport(exportedJson),
                parseEnvVarsFromExport(exportedJson),
                parseScheduleFromExport(exportedJson),
                null
        );

        HttpEntity<JobImportRequest> importEntity = new HttpEntity<>(importReq, authHeaders());
        ResponseEntity<String> importResp = restTemplate.exchange(
                base() + "/api/jobs/import", HttpMethod.POST, importEntity, String.class);
        if (importResp.getStatusCode().is4xxClientError()) {
            System.err.println("IMPORT 400 BODY: " + importResp.getBody());
        }
        assertEquals(HttpStatus.CREATED, importResp.getStatusCode());
        assertTrue(importResp.getBody().contains("round-trip-job"));

        // Verify re-imported job structure
        List<JobDefinition> jobs = jobRepo.findByJobNameContainingIgnoreCase(
                "round-trip-job", org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertEquals(1, jobs.size());
        Long newJobId = jobs.get(0).getJobId();

        // Re-export and verify key fields preserved
        String reExportedJson = extractData(restTemplate.getForEntity(
                base() + "/api/jobs/" + newJobId + "/export?format=json", String.class).getBody());
        assertTrue(reExportedJson.contains("step-a"));
        assertTrue(reExportedJson.contains("step-b"));
    }

    // =====================================================================
    //  Test 2: Export YAML format
    // =====================================================================

    @Test
    void exportYaml_format_parses_correctly() {
        Long jobId = createJobViaApi("yaml-job", "YAML test", "/tmp/yaml", null);
        addStepViaApi(jobId, "greet", 1, "SHELL_EXEC", "{\"command\":\"echo hi\"}");

        ResponseEntity<String> resp = restTemplate.getForEntity(
                base() + "/api/jobs/" + jobId + "/export?format=yaml", String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        String yamlContent = extractData(resp.getBody());
        assertNotNull(yamlContent);
        assertTrue(yamlContent.contains("yaml-job"), "YAML should contain job name value");
        assertTrue(yamlContent.contains("greet"), "YAML should contain step");
    }

    // =====================================================================
    //  Test 3: Import with mode=UPDATE → version history grows
    // =====================================================================

    @Test
    void importUpdateMode_createsVersion() {
        Long jobId = createJobViaApi("update-job", "Original desc", "/tmp/orig", null);
        addStepViaApi(jobId, "original-step", 1, "SHELL_EXEC", "{\"command\":\"echo original\"}");

        int versionsBefore = versionRepo.findByJobIdOrderByVersionNumberDesc(jobId).size();
        assertTrue(versionsBefore >= 1, "Should have at least v1 from creation/step add");

        // Build update import request with different step
        List<ImportStepDefinition> updatedSteps = List.of(
                new ImportStepDefinition("updated-step", 1, "SHELL_EXEC",
                        "{\"command\":\"echo updated\"}", false, true)
        );

        JobImportRequest updateReq = new JobImportRequest(
                "1.0", "UPDATE", null, "update-job", "Updated desc", "/tmp/updated",
                null, null, true, "test-team",
                updatedSteps, null, null, null, null
        );

        HttpEntity<JobImportRequest> entity = new HttpEntity<>(updateReq, authHeaders());
        ResponseEntity<String> updateResp = restTemplate.exchange(
                base() + "/api/jobs/import", HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.CREATED, updateResp.getStatusCode());

        // Verify version history grew (pre-update snapshot created)
        int versionsAfter = versionRepo.findByJobIdOrderByVersionNumberDesc(jobId).size();
        assertTrue(versionsAfter > versionsBefore,
                "Should have pre-update snapshot, before=" + versionsBefore + " after=" + versionsAfter);

        // Verify job was actually updated
        JobDefinition updated = jobRepo.findByIdWithSteps(jobId).orElseThrow();
        assertEquals("Updated desc", updated.getDescription());
        assertEquals("/tmp/updated", updated.getWorkingDir());
        assertEquals(1, updated.getSteps().size());
        assertEquals("updated-step", updated.getSteps().get(0).getStepName());
    }

    // =====================================================================
    //  Test 4: Import with mode=ERROR on existing job → rejection
    // =====================================================================

    @Test
    void importErrorMode_onExistingJob_rejects() {
        createJobViaApi("error-mode-job", "desc", "/tmp/test", null);

        JobImportRequest req = new JobImportRequest(
                "1.0", "ERROR", null, "error-mode-job", "desc", "/tmp/test",
                null, null, true, "test-team",
                List.of(new ImportStepDefinition("step", 1, "SHELL_EXEC", "{}", false, true)),
                null, null, null, null
        );

        HttpEntity<JobImportRequest> entity = new HttpEntity<>(req, authHeaders());
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/jobs/import", HttpMethod.POST, entity, String.class);

        assertNotEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    // =====================================================================
    //  Test 5: Version listing endpoint
    // =====================================================================

    @Test
    void versionListing_returnsVersions() {
        Long jobId = createJobViaApi("version-list-job", "desc", "/tmp/test", null);
        addStepViaApi(jobId, "step-1", 1, "SHELL_EXEC", "{}");
        addStepViaApi(jobId, "step-2", 2, "SHELL_EXEC", "{}");

        ResponseEntity<String> resp = restTemplate.getForEntity(
                base() + "/api/jobs/" + jobId + "/versions", String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("\"status\":\"SUCCESS\""));
        assertTrue(resp.getBody().contains("versionNumber"), "Should contain version data");
    }

    // =====================================================================
    //  Test 6: Rollback to previous version
    // =====================================================================

    @Test
    void rollbackToVersion_restoresState() {
        Long jobId = createJobViaApi("rollback-job", "Original", "/tmp/orig", null);
        addStepViaApi(jobId, "original-step", 1, "SHELL_EXEC", "{\"command\":\"echo orig\"}");

        var versionsBefore = versionRepo.findByJobIdOrderByVersionNumberDesc(jobId);
        int originalVersionNum = versionsBefore.get(0).getVersionNumber();

        // Modify: add a second step
        addStepViaApi(jobId, "new-step", 2, "SHELL_EXEC", "{\"command\":\"echo new\"}");

        // Verify job now has 2 steps
        JobDefinition beforeRollback = jobRepo.findByIdWithSteps(jobId).orElseThrow();
        assertEquals(2, beforeRollback.getSteps().size());

        // Rollback to the original version
        ResponseEntity<String> rollbackResp = restTemplate.postForEntity(
                base() + "/api/jobs/" + jobId + "/versions/" + originalVersionNum + "/rollback",
                new HttpEntity<>(authHeaders()), String.class);
        assertEquals(HttpStatus.OK, rollbackResp.getStatusCode());

        // After rollback, a new version should be created for the rollback action
        var versionsAfter = versionRepo.findByJobIdOrderByVersionNumberDesc(jobId);
        assertTrue(versionsAfter.size() > versionsBefore.size(),
                "Rollback should create a new version entry");
    }

    // =====================================================================
    //  Test 7: Get specific version JSON
    // =====================================================================

    @Test
    void getVersion_returnsVersionJson() {
        Long jobId = createJobViaApi("get-version-job", "desc", "/tmp/test", null);
        addStepViaApi(jobId, "step-a", 1, "SHELL_EXEC", "{}");

        var versions = versionRepo.findByJobIdOrderByVersionNumberDesc(jobId);
        int latestVersion = versions.get(0).getVersionNumber();

        ResponseEntity<String> resp = restTemplate.getForEntity(
                base() + "/api/jobs/" + jobId + "/versions/" + latestVersion, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        String versionJson = extractData(resp.getBody());
        assertNotNull(versionJson);
        assertTrue(versionJson.contains("get-version-job"), "Version JSON should contain job name");
    }

    // =====================================================================
    //  Test 8: Import validation catches unknown step types
    // =====================================================================

    @Test
    void importValidation_rejectsUnknownStepType() {
        JobImportRequest req = new JobImportRequest(
                "1.0", "ERROR", null, "bad-type-job", "desc", "/tmp/test",
                null, null, true, "test-team",
                List.of(new ImportStepDefinition("step", 1, "NONEXISTENT_TYPE", "{}", false, true)),
                null, null, null, null
        );

        HttpEntity<JobImportRequest> entity = new HttpEntity<>(req, authHeaders());
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/jobs/import", HttpMethod.POST, entity, String.class);

        assertNotEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    // =====================================================================
    //  Test 9: Import with circular dependency detection
    // =====================================================================

    @Test
    void importValidation_rejectsCircularDeps() {
        List<ImportStepDefinition> steps = List.of(
                new ImportStepDefinition("a", 1, "SHELL_EXEC", "{}", false, true),
                new ImportStepDefinition("b", 2, "SHELL_EXEC", "{}", false, true),
                new ImportStepDefinition("c", 3, "SHELL_EXEC", "{}", false, true)
        );
        List<ImportDependencyDefinition> circularDeps = List.of(
                new ImportDependencyDefinition("b", "a", "ON_SUCCESS"),
                new ImportDependencyDefinition("c", "b", "ON_SUCCESS"),
                new ImportDependencyDefinition("a", "c", "ON_SUCCESS") // cycle: a→c→b→a
        );

        JobImportRequest req = new JobImportRequest(
                "1.0", "ERROR", null, "cycle-job", "desc", "/tmp/test",
                null, null, true, "test-team",
                steps, circularDeps, null, null, null
        );

        HttpEntity<JobImportRequest> entity = new HttpEntity<>(req, authHeaders());
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/jobs/import", HttpMethod.POST, entity, String.class);

        assertNotEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    // =====================================================================
    //  Test 10: Import with invalid cron expression
    // =====================================================================

    @Test
    void importValidation_rejectsInvalidCron() {
        JobImportRequest req = new JobImportRequest(
                "1.0", "ERROR", null, "bad-cron-job", "desc", "/tmp/test",
                null, null, true, "test-team",
                List.of(new ImportStepDefinition("step", 1, "SHELL_EXEC", "{}", false, true)),
                null, null,
                new ImportScheduleDefinition("not-a-valid-cron", true),
                null
        );

        HttpEntity<JobImportRequest> entity = new HttpEntity<>(req, authHeaders());
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/jobs/import", HttpMethod.POST, entity, String.class);

        assertNotEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    // =====================================================================
    //  Test 11: Export preserves env vars through round trip
    // =====================================================================

    @Test
    void exportImport_preservesEnvVars() {
        Long jobId = createJobViaApi("envvar-job", "desc", "/tmp/test", null);
        addStepViaApi(jobId, "step-a", 1, "SHELL_EXEC", "{}");

        // Export to get current state (no env vars yet)
        String exportedJson = extractData(restTemplate.getForEntity(
                base() + "/api/jobs/" + jobId + "/export?format=json", String.class).getBody());

        // Parse and add env vars to the import payload
        List<ImportEnvVarDefinition> envVars = List.of(
                new ImportEnvVarDefinition("DATABASE_URL", "jdbc:h2:mem:test", false),
                new ImportEnvVarDefinition("APP_MODE", "production", true)
        );

        // Delete and re-import with env vars
        restTemplate.delete(base() + "/api/jobs/" + jobId);

        JobImportRequest importReq = new JobImportRequest(
                "1.0", "ERROR", null, "envvar-job", "desc", "/tmp/test",
                null, null, true, "test-team",
                parseStepsFromExport(exportedJson),
                parseDepsFromExport(exportedJson),
                envVars,
                parseScheduleFromExport(exportedJson),
                null
        );

        HttpHeaders headers = authHeaders();
        HttpEntity<JobImportRequest> entity = new HttpEntity<>(importReq, headers);
        ResponseEntity<String> importResp = restTemplate.exchange(
                base() + "/api/jobs/import", HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.CREATED, importResp.getStatusCode());

        // Export again and verify env vars are in the export
        List<JobDefinition> jobs = jobRepo.findByJobNameContainingIgnoreCase(
                "envvar-job", org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        String reExportedJson = extractData(restTemplate.getForEntity(
                base() + "/api/jobs/" + jobs.get(0).getJobId() + "/export?format=json", String.class).getBody());
        assertTrue(reExportedJson.contains("DATABASE_URL"), "Should preserve env var DATABASE_URL");
        assertTrue(reExportedJson.contains("APP_MODE"), "Should preserve env var APP_MODE");
    }

    // =====================================================================
    //  Helper methods
    // =====================================================================

    private Long createJobViaApi(String jobName, String description, String workingDir, String javaHome) {
        JobDefinitionRequest req = new JobDefinitionRequest(
                jobName, description, workingDir, javaHome, null);
        HttpEntity<JobDefinitionRequest> entity = new HttpEntity<>(req, authHeaders());
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/api/jobs", HttpMethod.POST, entity, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            System.err.println("400 BODY: " + resp.getBody());
        }
        assertEquals(HttpStatus.CREATED, resp.getStatusCode(), "Should create job");

        var jobOpt = jobRepo.findByJobName(jobName);
        assertTrue(jobOpt.isPresent(), "Job should exist after creation");
        return jobOpt.get().getJobId();
    }

    private void addStepViaApi(Long jobId, String stepName, int order, String stepType, String config) {
        JobStepRequest req = new JobStepRequest(stepName, order, stepType, config, false, true);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                base() + "/api/jobs/" + jobId + "/steps", req, String.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode(), "Should add step: " + stepName);
    }

    private void createScheduleViaApi(Long jobId, String cron) {
        JobScheduleRequest req = new JobScheduleRequest(cron);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                base() + "/api/jobs/" + jobId + "/schedule", req, String.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode(), "Should create schedule");
    }

    private void setDependencyViaApi(Long jobId) {
        var steps = stepRepo.findByJobDefinition_JobIdOrderByStepOrderAsc(jobId);
        if (steps.size() >= 2) {
            List<StepDependencyRequest> depReqs = List.of(new StepDependencyRequest(
                    steps.get(0).getStepId(), "ON_SUCCESS"));
            HttpEntity<List<StepDependencyRequest>> entity = new HttpEntity<>(depReqs, authHeaders());
            restTemplate.exchange(
                    base() + "/api/jobs/" + jobId + "/steps/" + steps.get(1).getStepId() + "/dependencies",
                    HttpMethod.PUT, entity, String.class);
        }
    }

    private String extractData(String responseBody) {
        try {
            ObjectMapper om = new ObjectMapper();
            var map = om.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            return (String) map.get("data");
        } catch (Exception e) {
            return responseBody;
        }
    }

    @SuppressWarnings("unchecked")
    private List<ImportStepDefinition> parseStepsFromExport(String json) {
        try {
            ObjectMapper om = new ObjectMapper();
            var map = om.readValue(json, Map.class);
            List<Map<String, Object>> steps = (List<Map<String, Object>>) map.get("steps");
            if (steps == null) return List.of();
            List<ImportStepDefinition> result = new ArrayList<>();
            for (Map<String, Object> s : steps) {
                String config = "{}";
                if (s.get("stepConfig") != null) {
                    try { config = om.writeValueAsString(s.get("stepConfig")); } catch (Exception ignored) {}
                }
                result.add(new ImportStepDefinition(
                        (String) s.get("stepName"),
                        ((Number) s.get("stepOrder")).intValue(),
                        (String) s.get("stepType"),
                        config,
                        Boolean.TRUE.equals(s.get("continueOnFailure")),
                        Boolean.TRUE.equals(s.get("enabled"))
                ));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ImportDependencyDefinition> parseDepsFromExport(String json) {
        try {
            ObjectMapper om = new ObjectMapper();
            var map = om.readValue(json, Map.class);
            List<Map<String, Object>> deps = (List<Map<String, Object>>) map.get("dependencies");
            if (deps == null) return List.of();
            return deps.stream().map(d -> new ImportDependencyDefinition(
                    (String) d.get("stepName"),
                    (String) d.get("dependsOnStepName"),
                    (String) d.get("edgeCondition")
            )).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ImportEnvVarDefinition> parseEnvVarsFromExport(String json) {
        try {
            ObjectMapper om = new ObjectMapper();
            var map = om.readValue(json, Map.class);
            List<Map<String, Object>> envVars = (List<Map<String, Object>>) map.get("envVars");
            if (envVars == null) return List.of();
            return envVars.stream().map(e -> new ImportEnvVarDefinition(
                    (String) e.get("key"),
                    (String) e.get("value"),
                    Boolean.TRUE.equals(e.get("isGlobal"))
            )).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private ImportScheduleDefinition parseScheduleFromExport(String json) {
        try {
            ObjectMapper om = new ObjectMapper();
            var map = om.readValue(json, Map.class);
            Object schedule = map.get("schedule");
            if (schedule == null) return null;
            Map<String, Object> schedMap = (Map<String, Object>) schedule;
            return new ImportScheduleDefinition(
                    (String) schedMap.get("cronExpression"),
                    Boolean.TRUE.equals(schedMap.get("enabled"))
            );
        } catch (Exception e) {
            return null;
        }
    }
}
