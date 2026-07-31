package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import java.time.LocalDateTime;

public record ApiResponse<T>(
    String status,
    T data,
    String error,
    LocalDateTime timestamp
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
