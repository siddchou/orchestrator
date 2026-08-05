package com.novakai.orchestrator.engine.observability;

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.engine.spi.StepStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ObservabilityService {

    private static final String OTHER_TAG = "__other__";

    private final MeterRegistry registry;
    private final AtomicInteger activeRuns = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Boolean> seenStepTypes = new ConcurrentHashMap<>();
    private final int maxStepTypeCardinality;
    // Track which collapsed types we've already warned about
    private final ConcurrentHashMap<String, Boolean> warnedCollapsedTypes = new ConcurrentHashMap<>();

    public ObservabilityService(MeterRegistry meterRegistry,
                                @Value("${orchestrator.metrics.max-step-type-cardinality:50}") int maxStepTypeCardinality) {
        this.registry = meterRegistry;
        this.maxStepTypeCardinality = maxStepTypeCardinality;

        Gauge.builder("orchestrator.run.active", activeRuns, AtomicInteger::get)
                .register(registry);
    }

    public void recordStepDuration(Duration duration, String stepType, StepStatus status) {
        Timer.builder("orchestrator.step.duration")
                .tag("step_type", resolveStepType(stepType))
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
                .tag("step_type", resolveStepType(stepType))
                .tag("status", status.name())
                .register(registry)
                .increment();
    }

    public void incrementActiveRuns() { activeRuns.incrementAndGet(); }
    public void decrementActiveRuns() { activeRuns.decrementAndGet(); }

    private String resolveStepType(String stepType) {
        if (seenStepTypes.containsKey(stepType)) return stepType;

        Boolean added = seenStepTypes.putIfAbsent(stepType, true);
        if (added != null) return stepType; // already known

        // New type — check cardinality cap
        if (seenStepTypes.size() > maxStepTypeCardinality) {
            warnOnce(stepType);
            return OTHER_TAG;
        }
        return stepType;
    }

    private void warnOnce(String stepType) {
        Boolean warned = warnedCollapsedTypes.putIfAbsent(stepType, true);
        if (warned == null) {
            log.warn("Step type '{}' collapsed to '{}' due to cardinality cap ({}) — " +
                    "increase orchestrator.metrics.max-step-type-cardinality if needed",
                    stepType, OTHER_TAG, maxStepTypeCardinality);
        }
    }
}
