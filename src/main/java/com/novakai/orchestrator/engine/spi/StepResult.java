package com.novakai.orchestrator.engine.spi;

import java.time.Duration;
import java.util.Map;

/**
 * Result of executing a single step. Replaces the old record
 * StepResult(boolean success, int exitCode, String logOutput).
 */
public record StepResult(
    StepStatus status,                    // SUCCESS / FAILED / SKIPPED
    Map<String, Object> outputs,          // structured outputs for Phase 3 templating
    String message,                       // human-readable summary (replaces old logOutput for metadata)
    Duration executionTime                // wall-clock time of this execute() call
) {
    public static StepResult success(Map<String, Object> outputs, String message, Duration time) {
        return new StepResult(StepStatus.SUCCESS, outputs, message, time);
    }

    public static StepResult failure(String message, Duration time) {
        return new StepResult(StepStatus.FAILED, Map.of(), message, time);
    }

    /** Backward compat: was the step successful? (maps to old boolean success field) */
    public boolean isSuccess() {
        return status == StepStatus.SUCCESS;
    }

    /** Backward compat: maps exit code from outputs if present, else -1 for failure. */
    public int getExitCode() {
        Object obj = outputs.get("exitCode");
        if (obj instanceof Number n) return n.intValue();
        return isSuccess() ? 0 : -1;
    }

    /** Backward compat: returns message as log output string for existing consumers. */
    public String getLogOutput() {
        return message != null ? message : "";
    }
}
