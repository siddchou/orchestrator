package com.novakai.orchestrator.api.service;

import com.novakai.orchestrator.api.dto.JobDefinitionRequest;
import com.novakai.orchestrator.api.dto.JobDefinitionResponse;
import com.novakai.orchestrator.api.dto.JobStepRequest;
import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.engine.exception.JobNotFoundException;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobDefinitionServiceTest {

    @Autowired
    private JobDefinitionService service;

    @Autowired
    private JobDefinitionRepository jobRepo;

    private Long savedJobId;

    @BeforeEach
    void setUp() {
        jobRepo.deleteAll();
        JobDefinition job = JobDefinition.builder()
                .jobName("existing-job")
                .description("A job")
                .workingDir("/tmp/test")
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        job = jobRepo.save(job);
        savedJobId = job.getJobId();
    }

    @Test
    void listJobs_returns_all() {
        var page = service.listJobs(null, PageRequest.of(0, 20));
        assertTrue(page.getTotalElements() >= 1);
    }

    @Test
    void listJobs_search_filters() {
        var page = service.listJobs("existing", PageRequest.of(0, 20));
        assertTrue(page.getContent().stream()
                .anyMatch(j -> j.jobName().equals("existing-job")));
    }

    @Test
    void createJob_success() {
        JobDefinitionRequest request = new JobDefinitionRequest(
                "new-job", "desc", "/tmp/new", null, null);
        JobDefinitionResponse response = service.createJob(request);
        assertNotNull(response.jobId());
        assertEquals("new-job", response.jobName());
    }

    @Test
    void createJob_duplicate_name_throws() {
        JobDefinitionRequest request = new JobDefinitionRequest(
                "existing-job", "desc", "/tmp", null, null);
        assertThrows(IllegalArgumentException.class, () -> service.createJob(request));
    }

    @Test
    void getJob_success() {
        JobDefinitionResponse response = service.getJob(savedJobId);
        assertEquals("existing-job", response.jobName());
    }

    @Test
    void getJob_not_found_throws() {
        assertThrows(JobNotFoundException.class, () -> service.getJob(99999L));
    }

    @Test
    void updateJob_success() {
        JobDefinitionRequest request = new JobDefinitionRequest(
                "renamed-job", "updated", "/tmp/updated", null, null);
        JobDefinitionResponse response = service.updateJob(savedJobId, request);
        assertEquals("renamed-job", response.jobName());
    }

    @Test
    void updateJob_to_taken_name_throws() {
        JobDefinition another = JobDefinition.builder()
                .jobName("other-job")
                .workingDir("/tmp/other")
                .enabled("Y")
                .steps(new ArrayList<>())
                .envVars(new ArrayList<>())
                .build();
        jobRepo.save(another);

        JobDefinitionRequest request = new JobDefinitionRequest(
                "other-job", "conflict", "/tmp", null, null);
        assertThrows(IllegalArgumentException.class, () -> service.updateJob(savedJobId, request));
    }

    @Test
    void deleteJob_success() {
        service.deleteJob(savedJobId);
        assertThrows(JobNotFoundException.class, () -> service.getJob(savedJobId));
    }

    @Test
    void deleteJob_not_found_throws() {
        assertThrows(JobNotFoundException.class, () -> service.deleteJob(99999L));
    }

    @Test
    void toggleEnabled_flips() {
        JobDefinitionResponse r1 = service.toggleEnabled(savedJobId);
        assertFalse(r1.enabled());
        JobDefinitionResponse r2 = service.toggleEnabled(savedJobId);
        assertTrue(r2.enabled());
    }

    @Test
    void addStep() {
        JobStepRequest step = new JobStepRequest(
                "env-setup", 1, "ENV_SETUP", "{}", false, true);
        var response = service.addStep(savedJobId, step);
        assertNotNull(response.stepId());
        assertEquals("env-setup", response.stepName());
    }
}
