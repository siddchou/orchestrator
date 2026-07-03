package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobSchedule;
import com.novakai.orchestrator.engine.exception.InvalidCronExpressionException;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobScheduleRepository;
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
class JobSchedulerServiceTest {

    @Autowired
    private JobSchedulerService schedulerService;

    @Autowired
    private JobScheduleRepository scheduleRepo;

    @Autowired
    private JobDefinitionRepository jobRepo;

    private Long savedJobId;

    @BeforeEach
    void setUp() {
        scheduleRepo.deleteAll();
        jobRepo.deleteAll();

        JobDefinition job = JobDefinition.builder()
                .jobName("scheduled-job")
                .workingDir("/tmp")
                .enabled("Y")
                .build();
        job = jobRepo.save(job);
        savedJobId = job.getJobId();
    }

    @Test
    void register_valid_schedule() {
        JobDefinition job = jobRepo.findById(savedJobId).orElseThrow();
        JobSchedule schedule = JobSchedule.builder()
                .jobDefinition(job)
                .cronExpression("0 0 0 * * *")
                .enabled("Y")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        schedule = scheduleRepo.save(schedule);

        schedulerService.register(schedule);
        // Should not throw
    }

    @Test
    void register_invalid_cron_throws() {
        JobDefinition job = jobRepo.findById(savedJobId).orElseThrow();
        JobSchedule schedule = JobSchedule.builder()
                .jobDefinition(job)
                .cronExpression("not-a-cron")
                .enabled("Y")
                .createdAt(LocalDateTime.now())
                .build();
        JobSchedule saved = scheduleRepo.save(schedule);

        assertThrows(InvalidCronExpressionException.class,
                () -> schedulerService.register(saved));
    }

    @Test
    void cancel_schedule() {
        JobDefinition job = jobRepo.findById(savedJobId).orElseThrow();
        JobSchedule schedule = JobSchedule.builder()
                .jobDefinition(job)
                .cronExpression("0 0 0 * * *")
                .enabled("Y")
                .createdAt(LocalDateTime.now())
                .build();
        schedule = scheduleRepo.save(schedule);

        schedulerService.register(schedule);
        schedulerService.cancel(schedule.getScheduleId());
        // Should not throw
    }

    @Test
    void updateSchedule_replaces() {
        JobDefinition job = jobRepo.findById(savedJobId).orElseThrow();
        JobSchedule schedule = JobSchedule.builder()
                .jobDefinition(job)
                .cronExpression("0 0 0 * * *")
                .enabled("Y")
                .createdAt(LocalDateTime.now())
                .build();
        schedule = scheduleRepo.save(schedule);

        schedulerService.register(schedule);

        schedule.setCronExpression("0 0 12 * * *");
        schedule = scheduleRepo.save(schedule);

        schedulerService.updateSchedule(schedule);
        // Should not throw
    }
}
