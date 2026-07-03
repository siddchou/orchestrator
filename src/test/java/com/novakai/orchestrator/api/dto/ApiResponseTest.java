package com.novakai.orchestrator.api.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void success_returns_success_status() {
        var response = ApiResponse.success("hello");
        assertEquals("SUCCESS", response.status());
        assertEquals("hello", response.data());
        assertNull(response.error());
        assertNotNull(response.timestamp());
    }

    @Test
    void error_returns_error_status() {
        var response = ApiResponse.error("something failed");
        assertEquals("ERROR", response.status());
        assertEquals("something failed", response.error());
        assertNull(response.data());
    }

    @Test
    void success_with_null_data() {
        var response = ApiResponse.success(null);
        assertEquals("SUCCESS", response.status());
        assertNull(response.data());
    }

    @Test
    void constructor_sets_all_fields() {
        var response = new ApiResponse<>("OK", "data", null, null);
        assertEquals("OK", response.status());
        assertEquals("data", response.data());
    }
}
