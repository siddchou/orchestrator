package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Standard API response envelope wrapping all endpoint responses")
public record ApiResponse<T>(
    @Schema(description = "Response status", example = "SUCCESS") String status,
    @Schema(description = "Response payload (null on error)") T data,
    @Schema(description = "Error message (null on success)", example = "Job not found") String error,
    @Schema(description = "Timestamp of response generation") LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success() {
        @SuppressWarnings("unchecked")
        ApiResponse<T> response = (ApiResponse<T>) new ApiResponse<>("SUCCESS", null, null, LocalDateTime.now());
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("ERROR", null, message, LocalDateTime.now());
    }
}
