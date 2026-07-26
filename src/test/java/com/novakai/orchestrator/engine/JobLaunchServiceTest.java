package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.engine.exception.JobAlreadyRunningException;
import com.novakai.orchestrator.engine.exception.JobNotFoundException;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobRunRepository;
import com.novakai.orchestrator.repository.JobStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobLaunchServiceTest {

    @Autowired
    private JobLaunchService launchService;

    @Autowired
    private JobDefinitionRepository jobRepo;

    @Autowired
    private JobRunRepository runRepo;

    @Autowired
    private JobStepRepository stepRepo;

    private Long savedJobId;

    @BeforeEach
    void setUp() {
        runRepo.deleteAll();
        stepRepo.deleteAll();
        jobRepo.deleteAll();

        JobDefinition job = JobDefinition.builder()
                .jobName("launchable-job")
                .workingDir(System.getProperty("user.dir"))
                .javaHome(extractJavaHome())
                .enabled("Y")
                .build();
        job = jobRepo.save(job);
        savedJobId = job.getJobId();

        JobStep step = JobStep.builder()
                .jobDefinition(job)
                .stepName("env-setup")
                .stepOrder(1)
                .stepType("ENV_SETUP")
                .stepConfig("{\"paths\":[]}")
                .enabled("Y")
                .continueOnFailure("N")
                .build();
        stepRepo.save(step);
    }

    @Test
    void launch_not_found() {
        assertThrows(JobNotFoundException.class,
                () -> launchService.launch(99999L, TriggerType.MANUAL, "test"));
    }

    @Test
    void launchByName_not_found() {
        assertThrows(JobNotFoundException.class,
                () -> launchService.launchByName("nonexistent", TriggerType.MANUAL, "test"));
    }

    @Test
    void cancel_nonexistent_run_does_not_throw() {
        launchService.cancel(99999L);
    }

    @Test
    void getLiveLogQueue_returns_null_for_unknown() {
        assertNull(launchService.getLiveLogQueue(99999L));
    }

    private static String extractJavaHome() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && javaHome.endsWith("jre")) {
            return javaHome.substring(0, javaHome.length() - 3);
        }
        return javaHome;
    }
}
