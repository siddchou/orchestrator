package com.novakai.orchestrator.domain.enums;

// @author Siddhant Choudhary

public enum RunStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    PARTIAL,
    CANCELLED,
    SKIPPED  // for Phase 3 DAG — step skipped due to unsatisfied edge condition
}
