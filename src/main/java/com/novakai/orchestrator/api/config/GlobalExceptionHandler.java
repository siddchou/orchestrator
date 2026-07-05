package com.novakai.orchestrator.api.config;

// @author Siddhant Choudhary

import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.engine.exception.InvalidCronExpressionException;
import com.novakai.orchestrator.engine.exception.JobAlreadyRunningException;
import com.novakai.orchestrator.engine.exception.JobNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(JobNotFoundException ex) {
        log.debug("Resource not found: {}", ex.getMessage());
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(JobAlreadyRunningException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleConflict(JobAlreadyRunningException ex) {
        log.warn("Conflict: job already running: {}", ex.getMessage());
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(InvalidCronExpressionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidCron(InvalidCronExpressionException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getBindingResult().getFieldErrors().size() + " error(s)");
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "invalid",
                (existing, replacement) -> existing
            ));
        return new ApiResponse<>("ERROR", errors, "Validation failed", LocalDateTime.now());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalState(IllegalStateException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getClass().getName(), ex);
        return ApiResponse.error("An internal error occurred. Please try again later.");
    }
}
