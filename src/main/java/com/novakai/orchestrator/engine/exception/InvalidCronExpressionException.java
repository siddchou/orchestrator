package com.novakai.orchestrator.engine.exception;

// @author Siddhant Choudhary

public class InvalidCronExpressionException extends RuntimeException {

    public InvalidCronExpressionException(String expression) {
        super("Invalid cron expression: " + expression);
    }
}
