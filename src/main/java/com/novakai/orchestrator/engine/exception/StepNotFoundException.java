package com.novakai.orchestrator.engine.exception;

public class StepNotFoundException extends RuntimeException {

    public StepNotFoundException(Long stepId) {
        super("Step not found with id: " + stepId);
    }
}
