package com.novakai.orchestrator.engine.exception;

// @author Siddhant Choudhary

public class JobAlreadyRunningException extends RuntimeException {

    public JobAlreadyRunningException(Long jobId) {
        super("Job is already running with id: " + jobId);
    }
}
