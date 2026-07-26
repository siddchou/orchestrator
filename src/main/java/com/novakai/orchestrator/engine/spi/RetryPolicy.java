package com.novakai.orchestrator.engine.spi;

import java.time.Duration;

/**
 * Declarative retry policy that an executor can declare as its default.
 * The orchestrator wraps execute() with this policy — executors don't implement retry themselves.
 */
public record RetryPolicy(
    int maxAttempts,              // 0 = no retry (execute once), 1+ = total attempts including first
    Duration delayBetweenAttempts // backoff interval; null means immediate retry
) {
    public static RetryPolicy none() {
        return new RetryPolicy(0, null);
    }

    public static RetryPolicy fixed(int attempts, Duration delay) {
        return new RetryPolicy(attempts - 1, delay); // attempts-1 retries after first attempt
    }

    /** Number of retries AFTER the initial attempt. */
    public int retries() {
        return Math.max(0, maxAttempts);
    }

    public boolean hasRetries() {
        return maxAttempts > 0;
    }
}
