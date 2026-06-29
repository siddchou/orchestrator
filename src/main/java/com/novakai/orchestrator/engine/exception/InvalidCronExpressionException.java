package com.novakai.orchestrator.engine.exception;

public class InvalidCronExpressionException extends RuntimeException {

    public InvalidCronExpressionException(String expression) {
        super("Invalid cron expression: " + expression);
    }
}
