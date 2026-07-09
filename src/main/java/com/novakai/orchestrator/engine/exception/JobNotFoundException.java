package com.novakai.orchestrator.engine.exception;

// @author Siddhant Choudhary

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(Long jobId) {
        super("Job not found with id: " + jobId);
    }

    public JobNotFoundException(String jobName) {
        super("Job not found with name: " + jobName);
    }
}
