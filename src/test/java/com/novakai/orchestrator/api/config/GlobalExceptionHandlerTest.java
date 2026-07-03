package com.novakai.orchestrator.api.config;

import com.novakai.orchestrator.engine.exception.InvalidCronExpressionException;
import com.novakai.orchestrator.engine.exception.JobAlreadyRunningException;
import com.novakai.orchestrator.engine.exception.JobNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound() {
        var response = handler.handleNotFound(new JobNotFoundException(1L));
        assertEquals("ERROR", response.status());
        assertNotNull(response.error());
    }

    @Test
    void handleConflict() {
        var response = handler.handleConflict(new JobAlreadyRunningException(1L));
        assertEquals("ERROR", response.status());
    }

    @Test
    void handleInvalidCron() {
        var response = handler.handleInvalidCron(new InvalidCronExpressionException("bad"));
        assertEquals("ERROR", response.status());
    }

    @Test
    void handleIllegalArgument() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("bad arg"));
        assertEquals("ERROR", response.status());
        assertEquals("bad arg", response.error());
    }

    @Test
    void handleIllegalState() {
        var response = handler.handleIllegalState(new IllegalStateException("bad state"));
        assertEquals("ERROR", response.status());
        assertEquals("bad state", response.error());
    }

    @Test
    void handleGeneral() {
        var response = handler.handleGeneral(new RuntimeException("unexpected"));
        assertEquals("ERROR", response.status());
        assertTrue(response.error().contains("Internal error"));
    }
}
