package com.novakai.orchestrator.engine;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StartupMaintenanceServiceTest {

    @Autowired
    private StartupMaintenanceService service;

    @Autowired
    private JobRunRepository runRepo;

    @Autowired
    private JobDefinitionRepository jobRepo;

    @BeforeEach
    void setUp() {
        runRepo.deleteAll();
        jobRepo.deleteAll();

        JobDefinition job = JobDefinition.builder()
                .jobName("test-job")
                .workingDir("/tmp")
                .enabled("Y")
                .build();
        jobRepo.save(job);
    }

    @Test
    void cleanupStaleRuns_marks_running_as_failed() {
        JobDefinition job = jobRepo.findAll().get(0);
        JobRun staleRun = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.SCHEDULED)
                .status(RunStatus.RUNNING)
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();
        runRepo.save(staleRun);

        service.cleanupStaleRuns();

        JobRun updated = runRepo.findById(staleRun.getRunId()).orElseThrow();
        assertEquals(RunStatus.FAILED, updated.getStatus());
        assertNotNull(updated.getEndedAt());
    }

    @Test
    void cleanupStaleRuns_no_running_runs() {
        JobDefinition job = jobRepo.findAll().get(0);
        JobRun completedRun = JobRun.builder()
                .jobDefinition(job)
                .triggerType(TriggerType.MANUAL)
                .status(RunStatus.SUCCESS)
                .createdAt(LocalDateTime.now().minusDays(1))
                .endedAt(LocalDateTime.now().minusDays(1))
                .build();
        runRepo.save(completedRun);

        service.cleanupStaleRuns();

        JobRun unchanged = runRepo.findById(completedRun.getRunId()).orElseThrow();
        assertEquals(RunStatus.SUCCESS, unchanged.getStatus());
    }

    @Test
    void cleanupStaleRuns_empty() {
        service.cleanupStaleRuns();
        // Should not throw
        assertTrue(runRepo.findAllByStatus(RunStatus.RUNNING).isEmpty());
    }
}
