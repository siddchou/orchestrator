package com.novakai.orchestrator.engine.spi;

/**
 * Terminal state of a step execution.
 */
public enum StepStatus {
    SUCCESS,
    FAILED,
    SKIPPED,
    CANCELLED
}
