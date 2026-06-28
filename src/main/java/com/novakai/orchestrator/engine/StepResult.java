package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.enums.StepType;

public record StepResult(
    boolean success,
    int exitCode,
    String logOutput
) {
    public static StepResult success(String log) {
        return new StepResult(true, 0, log);
    }

    public static StepResult failure(int exitCode, String log) {
        return new StepResult(false, exitCode, log);
    }

    public static StepResult failure(String log) {
        return new StepResult(false, -1, log);
    }
}
