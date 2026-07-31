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

/** Test executor that always returns FAILED. Configurable message via "message" param. */
@Component
public class FailStepExecutor implements StepExecutor {

    @Override
    public String getType() {
        return "FAIL";
    }

    @Override
    public StepConfigSchema getConfigSchema() {
        return new StepConfigSchema("FAIL", "Fail (test)", List.of(
            new FieldDefinition("message", "Failure message", FieldType.STRING, false, null, null, null)
        ));
    }

    @Override
    public StepResult execute(StepContext ctx) throws Exception {
        Object msg = ctx.getResolvedParams().get("message");
        return StepResult.failure(msg != null ? msg.toString() : "Test failure", Duration.ZERO);
    }

    @Override
    public RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.none();
    }
}
