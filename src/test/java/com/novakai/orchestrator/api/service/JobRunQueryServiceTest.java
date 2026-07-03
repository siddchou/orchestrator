package com.novakai.orchestrator.api.service;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobRunQueryServiceTest {

    @Autowired
    private JobRunQueryService service;

    @Autowired
    private JobRunRepository runRepo;

    @Autowired
    private JobDefinitionRepository jobRepo;

    private Long savedJobId;

    @BeforeEach
    void setUp() {
        runRepo.deleteAll();
        jobRepo.deleteAll();

        JobDefinition job = JobDefinition.builder()
                .jobName("test-job")
                .workingDir("/tmp")
                .enabled("Y")
                .build();
        job = jobRepo.save(job);
        savedJobId = job.getJobId();

        JobRun run1 = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .triggeredBy("admin")
                .status(RunStatus.SUCCESS)
                .createdAt(LocalDateTime.now().minusDays(2))
                .startedAt(LocalDateTime.now().minusDays(2))
                .endedAt(LocalDateTime.now().minusDays(2))
                .build();

        JobRun run2 = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.SCHEDULED)
                .triggeredBy("scheduler")
                .status(RunStatus.FAILED)
                .createdAt(LocalDateTime.now().minusDays(1))
                .startedAt(LocalDateTime.now().minusDays(1))
                .endedAt(LocalDateTime.now().minusDays(1))
                .build();

        runRepo.save(run1);
        runRepo.save(run2);
    }

    @Test
    void listRuns_returns_all() {
        var page = service.listRuns(null, null, null, null,
                PageRequest.of(0, 20, Sort.by("createdAt").descending()));
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void listRuns_filtered_by_job_id() {
        var page = service.listRuns(savedJobId, null, null, null,
                PageRequest.of(0, 20));
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void listRuns_filtered_by_status() {
        var page = service.listRuns(null, RunStatus.SUCCESS, null, null,
                PageRequest.of(0, 20));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void listRuns_filtered_by_date_range() {
        var page = service.listRuns(null, null,
                LocalDate.now().minusDays(3), LocalDate.now(),
                PageRequest.of(0, 20));
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void listRuns_empty_outside_date_range() {
        var page = service.listRuns(null, null,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                PageRequest.of(0, 20));
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void getRunDetail_success() {
        JobRun run = runRepo.findAll().get(0);
        var detail = service.getRunDetail(run.getRunId());
        assertEquals(run.getRunId(), detail.runId());
        assertEquals(run.getStatus(), detail.status());
    }

    @Test
    void getRunDetail_not_found() {
        assertThrows(Exception.class, () -> service.getRunDetail(99999L));
    }

    @Test
    void toRunSummary() {
        JobRun run = runRepo.findAll().get(0);
        var summary = service.toRunSummary(run);
        assertEquals(run.getRunId(), summary.runId());
        assertEquals(run.getStatus(), summary.status());
    }

    @Test
    void getStepLog_empty_for_run() {
        JobRun run = runRepo.findAll().get(0);
        var detail = service.getRunDetail(run.getRunId());
        assertTrue(detail.steps().isEmpty());
    }
}
