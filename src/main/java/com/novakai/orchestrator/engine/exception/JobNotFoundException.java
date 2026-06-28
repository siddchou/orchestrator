package com.novakai.orchestrator.engine.exception;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(Long jobId) {
        super("Job not found with id: " + jobId);
    }
}
