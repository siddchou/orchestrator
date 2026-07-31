package com.novakai.orchestrator.engine.spi;

/**
 * Terminal state of a step execution.
 */
public enum StepStatus {
    SUCCESS,
    FAILED,
    SKIPPED   // for Phase 3 conditional execution; not used in Phase 1 but reserved now
}
