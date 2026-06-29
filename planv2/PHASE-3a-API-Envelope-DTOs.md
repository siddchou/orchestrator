# Phase 3a — API Foundation: Response Envelope & DTOs

> **Goal:** Define the standard response wrapper, global exception handler, and all
> request/response DTOs used across every API endpoint. This is the shared contract
> that Phase 3b and 3c controllers depend on — implement this file completely first.

> **Depends on:** Phase 1 (entities, enums), Phase 2 (engine exceptions)  
> **Produces:** `ApiResponse<T>`, `GlobalExceptionHandler`, all DTO records

---

## Package Layout for This Sub-Phase

```
com.yourco.orchestrator/
├── api/
│   ├── ApiResponse.java
│   ├── GlobalExceptionHandler.java
│   └── dto/
│       ├── job/
│       │   ├── JobDefinitionRequest.java
│       │   ├── JobDefinitionResponse.java
│       │   ├── JobStepRequest.java
│       │   ├── JobStepResponse.java
│       │   ├── StepReorderRequest.java
│       │   ├── EnvVarRequest.java
│       │   ├── EnvVarResponse.java
│       │   ├── JobScheduleRequest.java
│       │   └── JobScheduleResponse.java
│       └── run/
│           ├── JobRunSummary.java
│           ├── JobRunDetail.java
│           └── RunStepDetail.java
└── exception/
    ├── JobNotFoundException.java
    ├── JobAlreadyRunningException.java
    └── InvalidCronExpressionException.java
```

---

## 3a.1 Standard Response Envelope

All endpoints — without exception — return this wrapper. Never return raw objects.

```java
// com.yourco.orchestrator.api.ApiResponse

public record ApiResponse<T>(
    String status,        // "SUCCESS" or "ERROR"
    T data,
    String error,
    LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("ERROR", null, message, LocalDateTime.now());
    }
}
```

---

## 3a.2 Custom Exception Classes

Define these before the exception handler. Keep them simple.

```java
// com.yourco.orchestrator.exception

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(Long id) {
        super("Job not found: " + id);
    }
}

public class JobAlreadyRunningException extends RuntimeException {
    public JobAlreadyRunningException(Long jobId) {
        super("Job " + jobId + " is already running");
    }
}

public class InvalidCronExpressionException extends RuntimeException {
    public InvalidCronExpressionException(String expression) {
        super("Invalid cron expression: " + expression);
    }
}

public class CredentialNotFoundException extends RuntimeException {
    public CredentialNotFoundException(String ref) {
        super("Credential not found: " + ref);
    }
}
```

---

## 3a.3 Global Exception Handler

One class handles all error responses. Place in the `api` package.

```java
// com.yourco.orchestrator.api.GlobalExceptionHandler

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(JobNotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(JobAlreadyRunningException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleConflict(JobAlreadyRunningException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(InvalidCronExpressionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadCron(InvalidCronExpressionException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"
            ));
        return new ApiResponse<>("ERROR", errors, "Validation failed", LocalDateTime.now());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbidden(AccessDeniedException ex) {
        return ApiResponse.error("Access denied");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiResponse.error("Internal error: " + ex.getMessage());
    }
}
```

---

## 3a.4 Job Definition DTOs

```java
// Request — used for create and update
public record JobDefinitionRequest(
    @NotBlank @Size(max = 200) String jobName,
    @Size(max = 1000)           String description,
    @NotBlank @Size(max = 500)  String workingDir
) {}

// Response — flattened, no lazy-loaded JPA collections
public record JobDefinitionResponse(
    Long jobId,
    String jobName,
    String description,
    String workingDir,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<JobStepResponse> steps,
    List<EnvVarResponse> envVars,
    JobScheduleResponse schedule      // null if no schedule configured
) {}
```

---

## 3a.5 Job Step DTOs

```java
public record JobStepRequest(
    @NotBlank @Size(max = 200)  String stepName,
    @NotNull                    Integer stepOrder,
    @NotNull                    StepType stepType,
    @NotBlank                   String stepConfig,    // JSON string, validated below
    boolean continueOnFailure,
    boolean enabled
) {
    // Custom validation: stepConfig must be valid JSON
    @AssertTrue(message = "stepConfig must be valid JSON")
    public boolean isStepConfigValidJson() {
        try {
            new ObjectMapper().readTree(stepConfig);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

public record JobStepResponse(
    Long stepId,
    String stepName,
    Integer stepOrder,
    StepType stepType,
    String stepConfig,
    boolean continueOnFailure,
    boolean enabled
) {}

// For the reorder endpoint — pass desired stepId order
public record StepReorderRequest(
    @NotEmpty List<Long> stepIds
) {}
```

---

## 3a.6 Environment Variable DTOs

```java
public record EnvVarRequest(
    @NotBlank @Size(max = 200)  String varName,
    @NotBlank @Size(max = 2000) String varValue
) {}

public record EnvVarResponse(
    Long envId,
    String varName,
    String varValue,
    boolean global
) {}
```

---

## 3a.7 Schedule DTOs

```java
public record JobScheduleRequest(
    @NotBlank String cronExpression
) {}

public record JobScheduleResponse(
    Long scheduleId,
    String cronExpression,
    boolean enabled,
    LocalDateTime nextFireTime    // null until first fire computed
) {}
```

---

## 3a.8 Job Run DTOs

```java
// Summary — used in list views and dashboard
public record JobRunSummary(
    Long runId,
    Long jobId,
    String jobName,
    RunStatus status,
    TriggerType triggerType,
    String triggeredBy,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    long durationSeconds
) {}

// Detail — used in run detail page; includes step-level breakdown
public record JobRunDetail(
    Long runId,
    Long jobId,
    String jobName,
    RunStatus status,
    TriggerType triggerType,
    String triggeredBy,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    long durationSeconds,
    List<RunStepDetail> steps
) {}

// Per-step summary in run detail view
// Log output is NOT included here — it's fetched separately to avoid large payloads
public record RunStepDetail(
    Long runStepId,
    String stepName,
    StepType stepType,
    Integer stepOrder,
    RunStatus status,
    Integer exitCode,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    long durationSeconds
) {}
```

---

## 3a.9 CORS Configuration

```java
// com.yourco.orchestrator.config.CorsConfig

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:4200")   // Angular dev server
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

---

## 3a.10 DTO Mapper Utility

Create a `DtoMapper` Spring component that converts JPA entities → DTOs.
Phase 3b and 3c controllers inject this. Using a hand-written mapper avoids
MapStruct complexity with lazy-loaded Oracle CLOB fields.

```java
// com.yourco.orchestrator.api.DtoMapper

@Component
public class DtoMapper {

    public JobDefinitionResponse toResponse(JobDefinition job) {
        return new JobDefinitionResponse(
            job.getJobId(),
            job.getJobName(),
            job.getDescription(),
            job.getWorkingDir(),
            "Y".equals(job.getEnabled()),
            job.getCreatedAt(),
            job.getUpdatedAt(),
            job.getSteps().stream().map(this::toResponse).toList(),
            job.getEnvVars().stream().map(this::toResponse).toList(),
            job.getSchedule() != null ? toResponse(job.getSchedule()) : null
        );
    }

    public JobStepResponse toResponse(JobStep step) {
        return new JobStepResponse(
            step.getStepId(),
            step.getStepName(),
            step.getStepOrder(),
            step.getStepType(),
            step.getStepConfig(),
            "Y".equals(step.getContinueOnFailure()),
            "Y".equals(step.getEnabled())
        );
    }

    public EnvVarResponse toResponse(JobEnvVar var) {
        return new EnvVarResponse(
            var.getEnvId(),
            var.getVarName(),
            var.getVarValue(),
            "Y".equals(var.getIsGlobal())
        );
    }

    public JobScheduleResponse toResponse(JobSchedule schedule) {
        return new JobScheduleResponse(
            schedule.getScheduleId(),
            schedule.getCronExpression(),
            "Y".equals(schedule.getEnabled()),
            schedule.getNextFireTime()
        );
    }

    public JobRunSummary toSummary(JobRun run) {
        long duration = 0;
        if (run.getStartedAt() != null && run.getEndedAt() != null) {
            duration = ChronoUnit.SECONDS.between(run.getStartedAt(), run.getEndedAt());
        }
        return new JobRunSummary(
            run.getRunId(),
            run.getJobDefinition().getJobId(),
            run.getJobDefinition().getJobName(),
            run.getStatus(),
            run.getTriggerType(),
            run.getTriggeredBy(),
            run.getStartedAt(),
            run.getEndedAt(),
            duration
        );
    }

    public RunStepDetail toDetail(JobRunStep rs) {
        long duration = 0;
        if (rs.getStartedAt() != null && rs.getEndedAt() != null) {
            duration = ChronoUnit.SECONDS.between(rs.getStartedAt(), rs.getEndedAt());
        }
        return new RunStepDetail(
            rs.getRunStepId(),
            rs.getJobStep().getStepName(),
            rs.getJobStep().getStepType(),
            rs.getStepOrder(),
            rs.getStatus(),
            rs.getExitCode(),
            rs.getStartedAt(),
            rs.getEndedAt(),
            duration
        );
    }
}
```

---

## Phase 3a Acceptance Criteria

- [ ] `ApiResponse.success(data)` and `ApiResponse.error(msg)` work correctly in unit tests
- [ ] `GlobalExceptionHandler` returns correct HTTP status for each exception type:
  - `JobNotFoundException` → 404
  - `JobAlreadyRunningException` → 409
  - `MethodArgumentNotValidException` → 400 with field map
  - Generic `Exception` → 500
- [ ] All DTO records compile with `@Valid` annotations in place
- [ ] `JobStepRequest.isStepConfigValidJson()` rejects malformed JSON strings
- [ ] `DtoMapper` converts entities → DTOs without triggering lazy-load exceptions

---

**Previous:** [Phase 2 — Engine](./PHASE-2-Engine.md)  
**Next:** [Phase 3b — Job Definition Controller](./PHASE-3b-API-JobDefinition-Controller.md)
