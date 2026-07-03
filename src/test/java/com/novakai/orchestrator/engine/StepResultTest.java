package com.novakai.orchestrator.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StepResultTest {

    @Test
    void success_returns_true() {
        StepResult result = StepResult.success("OK");
        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertEquals("OK", result.logOutput());
    }

    @Test
    void failure_returns_false() {
        StepResult result = StepResult.failure("FAILED");
        assertFalse(result.success());
        assertEquals(-1, result.exitCode());
        assertEquals("FAILED", result.logOutput());
    }

    @Test
    void failure_with_exit_code() {
        StepResult result = StepResult.failure(42, "custom error");
        assertFalse(result.success());
        assertEquals(42, result.exitCode());
        assertEquals("custom error", result.logOutput());
    }

    @Test
    void empty_output() {
        StepResult result = StepResult.success("");
        assertEquals("", result.logOutput());
    }

    @Test
    void constructor_sets_all_fields() {
        StepResult result = new StepResult(true, 0, "direct");
        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertEquals("direct", result.logOutput());
    }
}
