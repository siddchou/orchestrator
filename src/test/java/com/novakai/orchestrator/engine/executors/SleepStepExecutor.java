package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.engine.spi.RetryPolicy;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepExecutor;
import com.novakai.orchestrator.engine.spi.StepResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Test executor that sleeps for a configurable number of milliseconds, then returns SUCCESS. */
@Component
public class SleepStepExecutor implements StepExecutor {

    @Override
    public String getType() {
        return "SLEEP";
    }

    @Override
    public StepConfigSchema getConfigSchema() {
        return new StepConfigSchema("SLEEP", "Sleep (test)", List.of(
            new FieldDefinition("durationMs", "Duration in ms", FieldType.NUMBER, true, null, null, null)
        ));
    }

    @Override
    public StepResult execute(StepContext ctx) throws Exception {
        Object dur = ctx.getResolvedParams().get("durationMs");
        long millis = dur instanceof Number ? ((Number) dur).longValue() : 100;
        Thread.sleep(millis);
        return StepResult.success(Map.of("slept", millis, "value", "done"), "Slept " + millis + "ms", Duration.ofMillis(millis));
    }

    @Override
    public RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.none();
    }
}
