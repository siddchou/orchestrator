package com.novakai.orchestrator.engine.observability;

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.engine.spi.StepStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ObservabilityService {

    private final MeterRegistry registry;
    private final AtomicInteger activeRuns = new AtomicInteger(0);

    public ObservabilityService(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("orchestrator.run.active", activeRuns, AtomicInteger::get)
                .register(registry);
    }

    public void recordStepDuration(Duration duration, String stepType, StepStatus status) {
        Timer.builder("orchestrator.step.duration")
                .tag("step_type", stepType)
                .tag("status", status.name())
                .register(registry)
                .record(duration);
    }

    public void recordRunDuration(Duration duration, String jobName, RunStatus status) {
        Timer.builder("orchestrator.run.duration")
                .tag("job_name", jobName)
                .tag("status", status.name())
                .register(registry)
                .record(duration);
    }

    public void incrementRunCount(RunStatus status) {
        Counter.builder("orchestrator.run.count")
                .tag("status", status.name())
                .register(registry)
                .increment();
    }

    public void incrementStepCount(String stepType, StepStatus status) {
        Counter.builder("orchestrator.step.count")
                .tag("step_type", stepType)
                .tag("status", status.name())
                .register(registry)
                .increment();
    }

    public void incrementActiveRuns() { activeRuns.incrementAndGet(); }
    public void decrementActiveRuns() { activeRuns.decrementAndGet(); }
}
