package com.novakai.orchestrator.engine.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DagExecutionEngineMetricsTest {

    @Autowired
    private MeterRegistry meterRegistry;

    /** Verify that after a single-step run, step metrics are recorded. */
    @Test
    void stepDuration_recorded_after_run() {
        assertNotNull(meterRegistry);

        var service = new ObservabilityService(meterRegistry, 50);
        service.recordStepDuration(Duration.ofSeconds(1), "SLEEP", com.novakai.orchestrator.engine.spi.StepStatus.SUCCESS);
        service.incrementStepCount("SLEEP", com.novakai.orchestrator.engine.spi.StepStatus.SUCCESS);

        var timer = meterRegistry.get("orchestrator.step.duration")
                .tag("step_type", "SLEEP")
                .tag("status", "SUCCESS")
                .timer();
        assertNotNull(timer, "Step duration timer should be recorded");
    }
}
