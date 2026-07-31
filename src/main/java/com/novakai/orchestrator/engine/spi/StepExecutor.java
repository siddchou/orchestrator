package com.novakai.orchestrator.engine.spi;

/**
 * Contract for a pluggable step type. Extends the existing StepExecutor interface
 * by adding schema and retry support, while keeping backward compat via default methods.
 */
public interface StepExecutor {

    /** Unique string identifier for this step type (e.g. "HTTP_CALL"). Replaces StepType enum key. */
    String getType();

    /** Machine-readable config schema — drives UI forms and runtime validation. */
    StepConfigSchema getConfigSchema();

    /** Execute the step. The orchestrator handles retry, timing, and result persistence. */
    StepResult execute(StepContext ctx) throws Exception;

    /** Default retry policy for this executor type. Override to enable retries. */
    default RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.none();
    }
}
