package com.novakai.orchestrator.engine.spi;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Test
    void none_hasNoRetries() {
        RetryPolicy policy = RetryPolicy.none();
        assertFalse(policy.hasRetries());
        assertEquals(0, policy.maxAttempts());
        assertNull(policy.delayBetweenAttempts());
    }

    @Test
    void fixed_three_attempts_gives_two_retries() {
        RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofSeconds(1));
        assertTrue(policy.hasRetries());
        assertEquals(2, policy.retries()); // 3 total attempts = 2 retries after first
        assertEquals(Duration.ofSeconds(1), policy.delayBetweenAttempts());
    }

    @Test
    void fixed_single_attempt_no_retry() {
        RetryPolicy policy = RetryPolicy.fixed(1, Duration.ofSeconds(1));
        assertFalse(policy.hasRetries());
        assertEquals(0, policy.retries());
    }
}
