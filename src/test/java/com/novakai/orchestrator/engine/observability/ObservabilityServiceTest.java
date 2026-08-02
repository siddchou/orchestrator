package com.novakai.orchestrator.engine.observability;

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.engine.spi.StepStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ObservabilityServiceTest {

    private MeterRegistry registry;
    private ObservabilityService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new ObservabilityService(registry);
    }

    @Test
    void recordStepDuration_creates_timer_with_correct_tags() {
        service.recordStepDuration(Duration.ofSeconds(2), "shell_exec", StepStatus.SUCCESS);

        var timer = registry.get("orchestrator.step.duration")
                .tag("step_type", "shell_exec")
                .tag("status", "SUCCESS")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void recordRunDuration_creates_timer_with_correct_tags() {
        service.recordRunDuration(Duration.ofMinutes(5), "my-job", RunStatus.SUCCESS);

        var timer = registry.get("orchestrator.run.duration")
                .tag("job_name", "my-job")
                .tag("status", "SUCCESS")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void incrementRunCount_increments_counter() {
        service.incrementRunCount(RunStatus.FAILED);

        var counter = registry.get("orchestrator.run.count")
                .tag("status", "FAILED")
                .counter();
        assertNotNull(counter);
        assertEquals(1, counter.count());
    }

    @Test
    void incrementStepCount_increments_counter() {
        service.incrementStepCount("db_query", StepStatus.SKIPPED);

        var counter = registry.get("orchestrator.step.count")
                .tag("step_type", "db_query")
                .tag("status", "SKIPPED")
                .counter();
        assertNotNull(counter);
        assertEquals(1, counter.count());
    }

    @Test
    void activeRuns_gauge_tracks_count() {
        service.incrementActiveRuns();
        service.incrementActiveRuns();

        var gauge = registry.get("orchestrator.run.active").gauge();
        assertNotNull(gauge);
        assertEquals(2, gauge.value());

        service.decrementActiveRuns();
        assertEquals(1, gauge.value());
    }
}
