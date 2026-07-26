package com.novakai.orchestrator.engine.spi;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StepResultTest {

    @Test
    void success_returns_true() {
        StepResult result = StepResult.success(Map.of(), "OK", Duration.ZERO);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getExitCode());
        assertEquals("OK", result.getLogOutput());
    }

    @Test
    void failure_returns_false() {
        StepResult result = StepResult.failure("FAILED", Duration.ZERO);
        assertFalse(result.isSuccess());
        assertEquals(-1, result.getExitCode());
        assertEquals("FAILED", result.getLogOutput());
    }

    @Test
    void success_with_exit_code_in_outputs() {
        StepResult result = StepResult.success(Map.of("exitCode", 42), "custom output", Duration.ZERO);
        assertTrue(result.isSuccess());
        assertEquals(42, result.getExitCode());
    }

    @Test
    void empty_output() {
        StepResult result = StepResult.success(Map.of(), "", Duration.ZERO);
        assertEquals("", result.getLogOutput());
    }

    @Test
    void constructor_sets_all_fields() {
        StepResult result = new StepResult(StepStatus.SUCCESS, Map.of("key", "val"), "direct", Duration.ofMillis(100));
        assertTrue(result.isSuccess());
        assertEquals("direct", result.getLogOutput());
        assertEquals(Duration.ofMillis(100), result.executionTime());
    }

    @Test
    void backward_compat_getLogOutput_null_safe() {
        StepResult result = new StepResult(StepStatus.FAILED, Map.of(), null, Duration.ZERO);
        assertEquals("", result.getLogOutput());
    }
}
