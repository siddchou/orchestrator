package com.novakai.orchestrator.engine.exception;

// @author Siddhant Choudhary

public class StepNotFoundException extends RuntimeException {

    public StepNotFoundException(Long stepId) {
        super("Step not found with id: " + stepId);
    }

    public StepNotFoundException(String stepName) {
        super("Step not found with name: " + stepName);
    }
}
