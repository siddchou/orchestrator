package com.novakai.orchestrator.engine.exception;

public class JobAlreadyRunningException extends RuntimeException {

    public JobAlreadyRunningException(Long jobId) {
        super("Job is already running with id: " + jobId);
    }
}
