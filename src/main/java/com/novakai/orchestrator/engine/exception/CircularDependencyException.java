package com.novakai.orchestrator.engine.exception;

/**
 * Thrown when the DAG contains a circular dependency among steps.
 */
public class CircularDependencyException extends RuntimeException {
    public CircularDependencyException(String message) {
        super(message);
    }
}
