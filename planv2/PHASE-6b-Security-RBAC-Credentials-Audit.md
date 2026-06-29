# Phase 6b — Security: RBAC, Credential Store & Audit Logging

> **Goal:** Add role-based access control via `@PreAuthorize`, build the encrypted
> credential management API, and implement AOP-based audit logging so every sensitive
> action is traceable.

> **Depends on:** Phase 6a (`SecurityConfig`, `@EnableMethodSecurity`),
> Phase 1 (`JOB_CREDENTIAL`, `AUDIT_LOG` tables from `V3` migration)  
> **Produces:** `@PreAuthorize` annotations on services, `CredentialController`,
> `CredentialDecryptionService`, `AuditAspect`

---

## 6b.1 Role Summary

| Role | What they can do |
|------|-----------------|
| `VIEWER` | Read-only: list jobs, view runs, view step logs |
| `OPERATOR` | Everything VIEWER can do + trigger runs manually + cancel runs |
| `ADMIN` | Full access: create/edit/delete jobs, manage schedules, global config, manage users, manage credentials |

Spring Security's `@EnableMethodSecurity` (enabled in Phase 6a) makes `@PreAuthorize`
available on any Spring bean method.

---

## 6b.2 Apply `@PreAuthorize` to Service Methods

Add the annotations to the service classes from previous phases. Only the annotations
are new — no logic changes.

### `JobDefinitionService`

```java
// Viewing — VIEWER and above
@PreAuthorize("hasAnyRole('VIEWER','OPERATOR','ADMIN')")
public Page<JobDefinitionResponse> listJobs(...) { ... }

@PreAuthorize("hasAnyRole('VIEWER','OPERATOR','ADMIN')")
public JobDefinitionResponse getJob(Long id) { ... }

// Mutations — ADMIN only
@PreAuthorize("hasRole('ADMIN')")
public JobDefinitionResponse createJob(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public JobDefinitionResponse updateJob(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public void deleteJob(Long id) { ... }

@PreAuthorize("hasRole('ADMIN')")
public JobDefinitionResponse toggleEnabled(Long id) { ... }

@PreAuthorize("hasRole('ADMIN')")
public JobStepResponse addStep(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public JobStepResponse updateStep(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public void deleteStep(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public List<JobStepResponse> reorderSteps(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public EnvVarResponse addEnvVar(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public void deleteEnvVar(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public JobScheduleResponse createSchedule(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public JobScheduleResponse updateSchedule(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public void deleteSchedule(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public JobScheduleResponse toggleSchedule(...) { ... }
```

### `JobLaunchService`

```java
// Trigger runs — OPERATOR and above
@PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
public JobRun launch(Long jobId, TriggerType triggerType, String triggeredBy) { ... }

// Cancel — OPERATOR and above
@PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
public void cancel(Long runId) { ... }
```

### `SystemController`

```java
// Global env var mutations — ADMIN only
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/env-vars/global")
public ApiResponse<EnvVarResponse> addGlobalEnvVar(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/env-vars/global/{envId}")
public void deleteGlobalEnvVar(...) { ... }
```

---

## 6b.3 `CredentialDecryptionService`

Uses AES-256-GCM to encrypt credentials at rest. The encryption key is read from
an environment variable — never from the DB or config files.

```java
// com.yourco.orchestrator.security.CredentialDecryptionService

@Service
public class CredentialDecryptionService {

    /**
     * Must be set in the system environment.
     * Exactly 32 ASCII characters = 256 bits for AES-256.
     */
    @Value("${ORCHESTRATOR_ENCRYPTION_KEY}")
    private String rawKey;

    public String encrypt(String plainText) throws Exception {
        SecretKey key = buildKey();
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // Prepend IV (12 bytes) to cipherText, then Base64-encode the lot
        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    public String decrypt(String encryptedBase64) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);
        byte[] iv         = Arrays.copyOfRange(combined, 0, 12);
        byte[] cipherText = Arrays.copyOfRange(combined, 12, combined.length);

        SecretKey key = buildKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    private SecretKey buildKey() {
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                "ORCHESTRATOR_ENCRYPTION_KEY must be exactly 32 characters");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
```

---

## 6b.4 `CredentialController`

The API manages named credentials. The raw (plain text) value is never returned
by any endpoint — only the reference name and type.

```java
// com.yourco.orchestrator.api.controller.CredentialController

@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")      // all credential endpoints are ADMIN-only
public class CredentialController {

    private final JobCredentialRepository credRepo;
    private final CredentialDecryptionService cryptoService;

    // List — returns ref + type ONLY, never the encrypted value
    @GetMapping
    public ApiResponse<List<CredentialSummary>> list() {
        List<CredentialSummary> summaries = credRepo.findAll().stream()
            .map(c -> new CredentialSummary(
                c.getCredentialId(), c.getCredentialRef(), c.getCredType()))
            .toList();
        return ApiResponse.success(summaries);
    }

    // Create — encrypts the value before persisting
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CredentialSummary> create(
            @Valid @RequestBody CredentialRequest request) throws Exception {

        if (credRepo.findByCredentialRef(request.ref()).isPresent()) {
            throw new IllegalArgumentException(
                "Credential reference already exists: " + request.ref());
        }

        String encrypted = cryptoService.encrypt(request.value());
        JobCredential cred = JobCredential.builder()
            .credentialRef(request.ref())
            .credType(CredentialType.valueOf(request.type()))
            .credValue(encrypted)
            .createdAt(LocalDateTime.now())
            .build();
        cred = credRepo.save(cred);

        return ApiResponse.success(
            new CredentialSummary(cred.getCredentialId(), cred.getCredentialRef(), cred.getCredType())
        );
    }

    // Update — re-encrypt with a new value; ref and type cannot be changed
    @PutMapping("/{id}")
    public ApiResponse<CredentialSummary> update(
            @PathVariable Long id,
            @Valid @RequestBody CredentialUpdateRequest request) throws Exception {

        JobCredential cred = credRepo.findById(id)
            .orElseThrow(() -> new JobNotFoundException(id));
        cred.setCredValue(cryptoService.encrypt(request.newValue()));
        credRepo.save(cred);

        return ApiResponse.success(
            new CredentialSummary(cred.getCredentialId(), cred.getCredentialRef(), cred.getCredType())
        );
    }

    // Delete
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        credRepo.deleteById(id);
    }
}

// DTOs
public record CredentialSummary(Long id, String ref, CredentialType type) {}

public record CredentialRequest(
    @NotBlank String ref,
    @NotBlank String type,    // "PASSWORD" or "SSH_KEY"
    @NotBlank String value    // plain text — encrypted server-side
) {}

public record CredentialUpdateRequest(
    @NotBlank String newValue
) {}
```

---

## 6b.5 Audit Logging — Custom Annotation + AOP Aspect

### Custom `@Auditable` Annotation

```java
// com.yourco.orchestrator.audit.Auditable

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {
    String action();         // e.g. "CREATE_JOB", "DELETE_JOB", "TRIGGER_RUN"
    String entityType() default "";
}
```

### `AuditAspect`

```java
// com.yourco.orchestrator.audit.AuditAspect

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditRepo;

    /**
     * Fires after a method annotated with @Auditable returns successfully.
     * Extracts the entity ID from the return value if it exposes getId() or similar.
     */
    @AfterReturning(
        pointcut = "@annotation(auditable)",
        returning = "result"
    )
    public void logAudit(JoinPoint jp, Auditable auditable, Object result) {
        String username = resolveUsername();
        Long entityId   = resolveEntityId(result);

        AuditLog entry = AuditLog.builder()
            .username(username)
            .action(auditable.action())
            .entityType(auditable.entityType().isBlank() ? null : auditable.entityType())
            .entityId(entityId)
            .detail(buildDetail(jp))
            .createdAt(LocalDateTime.now())
            .build();

        try {
            auditRepo.save(entry);
        } catch (Exception ex) {
            // Audit failure must NEVER fail the main operation
            log.error("Failed to write audit log entry: {}", ex.getMessage());
        }
    }

    /**
     * Also fires on exception so failed sensitive operations are logged.
     */
    @AfterThrowing(
        pointcut = "@annotation(auditable)",
        throwing = "ex"
    )
    public void logAuditFailure(JoinPoint jp, Auditable auditable, Exception ex) {
        String username = resolveUsername();
        AuditLog entry = AuditLog.builder()
            .username(username)
            .action(auditable.action() + "_FAILED")
            .entityType(auditable.entityType().isBlank() ? null : auditable.entityType())
            .detail(ex.getMessage())
            .createdAt(LocalDateTime.now())
            .build();
        try {
            auditRepo.save(entry);
        } catch (Exception saveEx) {
            log.error("Failed to write failure audit log: {}", saveEx.getMessage());
        }
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private Long resolveEntityId(Object result) {
        if (result == null) return null;
        try {
            // Works for DTOs that have getJobId(), getRunId(), getScheduleId(), etc.
            for (String getter : List.of("getJobId","getRunId","getStepId","getScheduleId","getId")) {
                try {
                    Object id = result.getClass().getMethod(getter).invoke(result);
                    if (id instanceof Long l) return l;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String buildDetail(JoinPoint jp) {
        // Include method arg summary for context (truncated)
        String args = Arrays.stream(jp.getArgs())
            .map(a -> a != null ? a.toString() : "null")
            .collect(Collectors.joining(", "));
        if (args.length() > 500) args = args.substring(0, 497) + "...";
        return jp.getSignature().getName() + "(" + args + ")";
    }
}
```

### `AuditLogRepository`

```java
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
        String entityType, Long entityId, Pageable pageable);
}
```

---

## 6b.6 Applying `@Auditable` to Key Service Methods

Add to `JobDefinitionService` and `JobLaunchService`:

```java
// JobDefinitionService

@Auditable(action = "CREATE_JOB", entityType = "JOB")
public JobDefinitionResponse createJob(JobDefinitionRequest request) { ... }

@Auditable(action = "UPDATE_JOB", entityType = "JOB")
public JobDefinitionResponse updateJob(Long id, JobDefinitionRequest request) { ... }

@Auditable(action = "DELETE_JOB", entityType = "JOB")
public void deleteJob(Long id) { ... }

@Auditable(action = "CREATE_SCHEDULE", entityType = "JOB")
public JobScheduleResponse createSchedule(Long jobId, JobScheduleRequest request) { ... }

@Auditable(action = "DELETE_SCHEDULE", entityType = "JOB")
public void deleteSchedule(Long jobId) { ... }

// JobLaunchService

@Auditable(action = "TRIGGER_RUN", entityType = "JOB_RUN")
public JobRun launch(Long jobId, TriggerType triggerType, String triggeredBy) { ... }

@Auditable(action = "CANCEL_RUN", entityType = "JOB_RUN")
public void cancel(Long runId) { ... }
```

---

## 6b.7 Audit Log Query Endpoint

Add to `SystemController` (ADMIN-only):

```java
@GetMapping("/audit-log")
@PreAuthorize("hasRole('ADMIN')")
public ApiResponse<Page<AuditLogResponse>> getAuditLog(
        @RequestParam(required = false) String username,
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) Long entityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size) {
    // Build query based on provided filters
    ...
}
```

---

## Phase 6b Acceptance Criteria

- [ ] `VIEWER` role calling `POST /api/jobs` returns `403 Forbidden`
- [ ] `OPERATOR` role calling `POST /api/jobs/{id}/run` returns `202 Accepted`
- [ ] `OPERATOR` role calling `DELETE /api/jobs/{id}` returns `403 Forbidden`
- [ ] `ADMIN` role has full access to all endpoints
- [ ] `POST /api/credentials` with a plain-text password stores an AES-GCM encrypted value in DB
- [ ] `GET /api/credentials` returns only `ref` and `type` — never the encrypted value
- [ ] Decrypted value round-trips correctly in a unit test: `decrypt(encrypt(plainText)) == plainText`
- [ ] Tampered ciphertext throws `AEADBadTagException` on decrypt (GCM authentication failure)
- [ ] `deleteJob()` creates an `AUDIT_LOG` row with action `DELETE_JOB`
- [ ] Failed `launch()` (e.g. job not found) creates an `AUDIT_LOG` row with action `TRIGGER_RUN_FAILED`
- [ ] Audit log entries are never missing even when the audited method throws

---

**Previous:** [Phase 6a — JWT Auth](./PHASE-6a-Security-JWT-Auth.md)  
**Next:** [Phase 7a — Deployment: Packaging & Systemd](./PHASE-7a-Deploy-Packaging-Systemd.md)
